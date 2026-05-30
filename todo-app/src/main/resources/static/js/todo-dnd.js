document.addEventListener('DOMContentLoaded', function () {
  const list = document.getElementById('todos');
  if (!list) return;

  const reorderUrl = document.getElementById('todo-list')?.getAttribute('data-reorder-url');
  if (!reorderUrl) return;

  new Sortable(list, {
    animation: 150,
    chosenClass: 'dragging',
    onEnd: function () {
      const ids = Array.from(list.querySelectorAll('li.todo-row')).map(li => li.getAttribute('data-id'));
      fetch(reorderUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(ids)
      }).catch(err => console.error('reorder failed', err));
    }
  });

  // Status selects
  document.querySelectorAll('.status-select').forEach(sel => {
    sel.addEventListener('change', function () {
      const id = this.getAttribute('data-id');
      const status = this.value;
      this.dataset.status = status;
      const row = this.closest('.todo-row');
      if (row) row.dataset.status = status;
      fetch(`/todos/${id}/status`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `status=${encodeURIComponent(status)}`
      }).catch(err => console.error('status update failed', err));
    });
  });

  // Snooze: toggle dropdown
  document.querySelectorAll('.action-btn--snooze').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      const wrap = this.closest('.snooze-wrap');
      const isOpen = wrap.classList.contains('open');
      document.querySelectorAll('.snooze-wrap.open').forEach(w => w.classList.remove('open'));
      if (!isOpen) wrap.classList.add('open');
    });
  });

  // Snooze: close on outside click
  document.addEventListener('click', () => {
    document.querySelectorAll('.snooze-wrap.open').forEach(w => w.classList.remove('open'));
  });

  // Snooze: apply option
  document.querySelectorAll('.snooze-menu button').forEach(btn => {
    btn.addEventListener('click', function (e) {
      e.stopPropagation();
      const wrap = this.closest('.snooze-wrap');
      const id = wrap.dataset.id;
      const days = this.dataset.days;
      fetch(`/todos/${id}/snooze`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `days=${days}`
      })
        .then(r => r.json())
        .then(data => {
          const row = wrap.closest('.todo-row');
          let deadlineSpan = row.querySelector('.meta .deadline-display');
          if (deadlineSpan) {
            deadlineSpan.textContent = '📅 ' + data.deadline;
          } else {
            const meta = row.querySelector('.meta');
            const span = document.createElement('span');
            span.className = 'deadline-display';
            span.textContent = '📅 ' + data.deadline;
            meta.prepend(span);
          }
          wrap.classList.remove('open');
        })
        .catch(err => console.error('snooze failed', err));
    });
  });
});
