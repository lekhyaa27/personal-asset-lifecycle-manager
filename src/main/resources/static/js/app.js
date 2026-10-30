// Show/hide the "Is maintenance/repair required?" form (Yes / No radio buttons)
document.querySelectorAll('[data-toggle-form]').forEach(box => {
  const form = document.querySelector(box.dataset.toggleForm);
  const no = document.querySelector(box.dataset.no);
  box.querySelectorAll('input[type=radio]').forEach(r => r.addEventListener('change', () => {
    form.classList.toggle('d-none', r.value !== 'yes');
    no.classList.toggle('d-none', r.value !== 'no');
  }));
});
// Confirmation dialog for forms with data-confirm="..."
document.querySelectorAll('form[data-confirm]').forEach(f => f.addEventListener('submit', e => {
  if (!confirm(f.dataset.confirm)) e.preventDefault();
}));
// "Edit" buttons on warranty / insurance rows copy their data-* values into #expiryForm
document.querySelectorAll('.edit-expiry').forEach(btn => btn.addEventListener('click', () => {
  const form = document.getElementById('expiryForm');
  Object.entries(btn.dataset).forEach(([k, v]) => { if (form.elements[k]) form.elements[k].value = v; });
  form.scrollIntoView({ behavior: 'smooth' });
}));
