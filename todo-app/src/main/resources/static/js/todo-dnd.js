document.addEventListener('DOMContentLoaded', function () {
  const list = document.getElementById('todos');
  if (!list) return;

  const reorderUrl = document.getElementById('todo-list')?.getAttribute('data-reorder-url');
  if (!reorderUrl) return;

  new Sortable(list, {
    animation: 150,
    onEnd: function (evt) {
      const ids = Array.from(list.querySelectorAll('li.todo-row')).map(li => li.getAttribute('data-id'));
      fetch(reorderUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(ids)
      }).catch(err => console.error('reorder failed', err));
    }
  });

  // status selects
  document.querySelectorAll('.status-select').forEach(sel => {
    sel.addEventListener('change', function () {
      const id = this.getAttribute('data-id');
      const status = this.value;
      fetch(`/todos/${id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `status=${encodeURIComponent(status)}`
      }).catch(err => console.error('status update failed', err));
    });
  });
});

