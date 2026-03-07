interface ToastDisplayItem {
  id: number;
  message: string;
  type: 'info' | 'success' | 'error';
}

export function ToastContainer({ toasts }: { toasts: ToastDisplayItem[] }) {
  return (
    <div className="toast-container">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast-${t.type}`}>
          {t.message}
        </div>
      ))}
    </div>
  );
}
