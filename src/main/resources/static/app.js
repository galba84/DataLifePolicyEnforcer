'use strict';
const $ = id => document.getElementById(id);
const state = {databases: [], policies: [], database: null, browseVersion: 0, tableVersion: 0, assignmentVersion: 0};
let csrf;
function element(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function message(text, error = false) { $('message').textContent = text; $('message').className = error ? 'error' : ''; }
async function api(path, method = 'GET', body) {
  const headers = {'Accept': 'application/json'};
  if (method !== 'GET') {
    if (!csrf) csrf = await api('/api/csrf');
    headers[csrf.headerName] = csrf.token;
  }
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const response = await fetch(path, {method, headers, credentials: 'same-origin', body: body === undefined ? undefined : JSON.stringify(body)});
  if (!response.ok) {
    if (response.status === 403) csrf = null;
    const problem = await response.json().catch(() => ({}));
    throw new Error(problem.detail || 'Request failed (' + response.status + ').');
  }
  return response.status === 204 ? null : response.json();
}
function bind(node, event, action) {
  node.addEventListener(event, async e => {
    e.preventDefault();
    const button = event === 'submit' ? node.querySelector('[type=submit]') : node;
    if (button instanceof HTMLButtonElement) button.disabled = true;
    try { await action(e); } catch (error) { message(error.message, true); }
    finally { if (button instanceof HTMLButtonElement) button.disabled = false; }
  });
}
function button(text, action, className) {
  const node = element('button', text, className); node.type = 'button'; bind(node, 'click', action); return node;
}
function grid(headers, rows) {
  const table = element('table'), head = element('thead'), tr = element('tr'), body = element('tbody');
  headers.forEach(h => tr.append(element('th', h))); head.append(tr);
  rows.forEach(row => { const r = element('tr'); row.forEach(value => { const td = element('td'); td.append(value instanceof Node ? value : document.createTextNode(value ?? '—')); r.append(td); }); body.append(r); });
  table.append(head, body); const wrap = element('div', null, 'table-wrap'); wrap.append(table); return wrap;
}
function options(select, values, placeholder) {
  const previous = select.value;
  select.replaceChildren(new Option(placeholder, ''));
  values.forEach(([value, label]) => select.add(new Option(label, value)));
  if (values.some(([value]) => String(value) === previous)) select.value = previous;
}
function bytes(value) { return new Intl.NumberFormat().format(value) + ' B'; }
function resetForm(id) { $(id).reset(); $(id).elements.namedItem('id').value = ''; }
function fillForm(id, record) {
  resetForm(id);
  for (const field of $(id).elements) {
    if (!field.name || !(field.name in record)) continue;
    if (field.type === 'checkbox') field.checked = record[field.name];
    else field.value = record[field.name] ?? '';
  }
}
async function loadDatabases() {
  state.databases = await api('/api/databases');
  $('databases').replaceChildren();
  if (!state.databases.length) $('databases').append(element('p', 'No databases configured. Add a connection below.', 'empty'));
  state.databases.forEach(db => {
    const card = element('div', null, 'card' + (state.database?.id === db.id ? ' selected' : ''));
    card.append(element('div', db.name, 'card-title'), element('p', db.host + ':' + db.port + '/' + db.database + ' · ' + db.status));
    const actions = element('div', null, 'actions');
    actions.append(button('Browse', () => browseDatabase(db)), button('Test', async () => {
      const result = await api('/api/databases/' + db.id + '/test', 'POST');
      await loadDatabases(); message(result.message, !result.reachable);
    }), button('Edit', () => {
      fillForm('database-form', db); $('database-editor').open = true; $('database-editor').scrollIntoView({block:'center'});
    }), button('Delete', async () => {
      if (!confirm('Remove connection "' + db.name + '" from this service?')) return;
      await api('/api/databases/' + db.id, 'DELETE');
      if (state.database?.id === db.id) clearBrowser();
      await loadDatabases(); message('Connection removed.');
    }, 'danger'));
    card.append(actions); $('databases').append(card);
  });
  options($('assignment-database'), state.databases.map(d => [d.id, d.name]), 'Select a database');
}
function clearBrowser() {
  state.database = null; state.browseVersion++; state.tableVersion++;
  $('selected-database').textContent = 'Select a database to browse.';
  $('schema').replaceChildren(new Option('Select a database first', '')); $('schema').disabled = true;
  $('tables').replaceChildren(); $('table-details').replaceChildren();
  $('assignment-schema').value = ''; $('assignment-table').value = '';
}
async function browseDatabase(db) {
  clearBrowser(); state.database = db;
  const version = ++state.browseVersion;
  $('selected-database').textContent = db.name + ' / ' + db.database;
  $('tables').textContent = 'Loading schemas…';
  const schemas = await api('/api/databases/' + db.id + '/schemas');
  if (version !== state.browseVersion) return;
  options($('schema'), schemas.map(s => [s, s]), 'Select a schema'); $('schema').disabled = false;
  $('tables').textContent = schemas.length ? 'Choose a schema.' : 'No user schemas found.';
  $('assignment-database').value = db.id;
}
async function loadTables() {
  const db = state.database, schema = $('schema').value, version = ++state.browseVersion;
  state.tableVersion++; $('table-details').replaceChildren(); $('assignment-table').value = '';
  if (!db || !schema) { $('tables').replaceChildren(); return; }
  $('tables').textContent = 'Loading tables…';
  const tables = await api('/api/databases/' + db.id + '/tables?schema=' + encodeURIComponent(schema));
  if (version !== state.browseVersion) return;
  $('tables').replaceChildren(tables.length ? grid(['Table', 'Rows (est.)', 'Total size', 'Kind'], tables.map(t => [
    button(t.table, () => showTable(db, t.schema, t.table), 'link'),
    t.estimatedRows < 0 ? 'Unknown' : t.estimatedRows, bytes(t.totalSize),
    t.partitioned ? 'Partitioned' : t.partition ? 'Partition' : 'Table'
  ])) : element('p', 'No tables in this schema.', 'empty'));
}
async function showTable(db, schema, name) {
  const version = ++state.tableVersion;
  $('table-details').textContent = 'Loading table details…';
  const data = await api('/api/databases/' + db.id + '/tables/' + encodeURIComponent(schema) + '/' + encodeURIComponent(name));
  if (version !== state.tableVersion || state.database?.id !== db.id) return;
  const panel = $('table-details'); panel.replaceChildren(element('h3', schema + '.' + name));
  panel.append(element('p', 'Table: ' + bytes(data.table.tableSize) + ' · Indexes: ' + bytes(data.table.indexSize) + ' · Total: ' + bytes(data.table.totalSize), 'muted'));
  panel.append(element('p', 'Primary key: ' + (data.primaryKeys.join(', ') || 'None'), 'muted'));
  panel.append(grid(['Column', 'Type', 'Nullable', 'Default'], data.columns.map(c => [c.name, c.dataType, c.nullable ? 'Yes' : 'No', element('code', c.defaultValue ?? '—')])));
  panel.append(element('h3', 'Indexes'), grid(['Name', 'Primary / unique', 'Definition', 'Size'], data.indexes.map(i => [
    i.name, i.primary ? 'Primary' : i.unique ? 'Unique' : 'No', element('code', i.definition), bytes(i.size)
  ])));
  panel.append(element('h3', 'Partitions'));
  if (data.table.partitioned) {
    panel.append(element('p', data.partitionStrategy + ' · ' + data.partitionKey));
    panel.append(element('p', 'Sizes show each relation’s own storage. Open a partition to inspect its children.', 'muted'));
    panel.append(grid(['Partition', 'Bounds', 'Total size'], data.partitions.map(p => [
      button(p.schema + '.' + p.table, () => showTable(db, p.schema, p.table), 'link'), element('code', p.bounds), bytes(p.totalSize)
    ])));
  } else panel.append(element('p', 'This table has no partition key.', 'muted'));
  $('assignment-database').value = db.id; $('assignment-schema').value = schema; $('assignment-table').value = name;
}
async function loadPolicies() {
  state.policies = await api('/api/policies');
  $('policies').replaceChildren();
  if (!state.policies.length) $('policies').append(element('p', 'No policies yet. Create a declarative lifecycle policy.', 'empty'));
  state.policies.forEach(p => {
    const card = element('div', null, 'card'), actions = element('div', null, 'actions');
    card.append(element('div', p.name, 'card-title'), element('p', 'Retention: ' + (p.retentionDays ?? 'unset') + ' days · Archive: ' + (p.archiveEnabled ? 'on' : 'off') + ' · Removal: ' + (p.deleteEnabled ? 'on' : 'off')));
    actions.append(button('Edit', () => {
      fillForm('policy-form', p); $('policy-editor').open = true; $('policy-editor').scrollIntoView({block:'center'});
    }), button('Assignments', async () => { $('assignment-policy').value = p.id; await loadAssignments(); }),
    button('Dry-run', () => dryRun(p)), button('Delete', async () => {
      if (!confirm('Delete policy "' + p.name + '" and its assignments?')) return;
      await api('/api/policies/' + p.id, 'DELETE'); await loadPolicies(); await loadAssignments();
      $('plan').replaceChildren(element('p', 'Run a policy to refresh the preview.', 'empty')); message('Policy deleted.');
    }, 'danger'));
    card.append(actions); $('policies').append(card);
  });
  options($('assignment-policy'), state.policies.map(p => [p.id, p.name]), 'Select a policy');
}
async function loadAssignments() {
  const id = $('assignment-policy').value, version = ++state.assignmentVersion;
  $('assignments').replaceChildren();
  if (!id) return;
  const assignments = await api('/api/policies/' + id + '/assignments');
  if (version !== state.assignmentVersion) return;
  $('assignments').replaceChildren(assignments.length ? grid(['Database', 'Table', ''], assignments.map(a => [
    state.databases.find(d => d.id === a.databaseId)?.name ?? a.databaseId,
    a.schemaName + '.' + a.tableName,
    button('Unassign', async () => {
      await api('/api/policies/' + id + '/assignments/' + a.id, 'DELETE'); await loadAssignments(); message('Assignment removed.');
    })
  ])) : element('p', 'This policy has no table assignments.', 'empty'));
}
async function dryRun(policy) {
  const plans = await api('/api/policies/' + policy.id + '/dry-run', 'POST');
  $('plan').replaceChildren(element('h3', policy.name));
  if (!plans.length) $('plan').append(element('p', 'Assign this policy to a table before running a preview.', 'empty'));
  for (const plan of plans) {
    const db = state.databases.find(d => d.id === plan.databaseId);
    $('plan').append(element('h3', (db?.name ?? plan.databaseId) + ' / ' + plan.schema + '.' + plan.table));
    $('plan').append(element('p', 'Evaluated at ' + plan.evaluatedAt + ' · Preview only', 'muted'));
    $('plan').append(grid(['Partition / table', 'Age (days)', 'Action', 'Reason', 'Bounds'], plan.actions.map(a => [
      a.schema + '.' + a.table, a.ageDays, element('span', a.action, 'status ' + a.action.toLowerCase()), a.reason, element('code', a.partitionBounds ?? '—')
    ])));
  }
  $('plan').scrollIntoView({block:'start'}); message('Dry-run complete. Target data was not changed.');
}
bind($('database-form'), 'submit', async () => {
  const form = $('database-form'), input = Object.fromEntries(new FormData(form)), id = input.id;
  delete input.id; input.port = Number(input.port);
  await api('/api/databases' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', input);
  if (state.database && String(state.database.id) === id) clearBrowser();
  resetForm('database-form'); await loadDatabases(); message('Database configuration saved.');
});
bind($('policy-form'), 'submit', async () => {
  const form = $('policy-form'), input = Object.fromEntries(new FormData(form)), id = input.id;
  delete input.id;
  for (const name of ['retentionDays','partitionIntervalDays','archiveAfterDays','deleteAfterDays']) input[name] = input[name] ? Number(input[name]) : null;
  for (const name of ['archiveEnabled','deleteEnabled']) input[name] = form.elements.namedItem(name).checked;
  for (const name of ['targetObjectStorage','executionSchedule']) input[name] = input[name] || null;
  input.dryRun = true;
  await api('/api/policies' + (id ? '/' + id : ''), id ? 'PUT' : 'POST', input);
  resetForm('policy-form'); await loadPolicies(); message('Policy saved in dry-run mode.');
});
bind($('assignment-form'), 'submit', async () => {
  const input = Object.fromEntries(new FormData($('assignment-form'))), id = input.policyId;
  delete input.policyId; input.databaseId = Number(input.databaseId);
  await api('/api/policies/' + id + '/assignments', 'POST', input);
  await loadAssignments(); message('Policy assigned.');
});
bind($('schema'), 'change', loadTables);
bind($('assignment-policy'), 'change', loadAssignments);
bind($('reset-database'), 'click', () => resetForm('database-form'));
bind($('new-policy'), 'click', () => { resetForm('policy-form'); $('policy-editor').open = true; $('policy-form').elements.namedItem('name').focus(); });
bind($('refresh'), 'click', async () => { await Promise.all([loadDatabases(), loadPolicies()]); await loadAssignments(); message('Configuration refreshed.'); });
Promise.all([loadDatabases(), loadPolicies()]).catch(error => message(error.message, true));

