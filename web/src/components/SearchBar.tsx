import { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';

interface SearchBarProps {
  initialQuery?: string;
  onSearch?: (query: string) => void;
  placeholder?: string;
}

export default function SearchBar({ initialQuery = '', onSearch, placeholder = '搜索视频...' }: SearchBarProps) {
  const [query, setQuery] = useState(initialQuery);
  const navigate = useNavigate();
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    setQuery(initialQuery);
  }, [initialQuery]);

  const handleChange = (value: string) => {
    setQuery(value);
    if (onSearch) {
      if (debounceRef.current) clearTimeout(debounceRef.current);
      debounceRef.current = setTimeout(() => {
        onSearch(value);
      }, 300);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const q = query.trim();
    if (q) {
      navigate(`/search?q=${encodeURIComponent(q)}`);
    }
  };

  return (
    <form className="search-bar" onSubmit={handleSubmit}>
      <input
        type="text"
        className="search-bar-input"
        placeholder={placeholder}
        value={query}
        onChange={(e) => handleChange(e.target.value)}
      />
      <button type="submit" className="search-bar-btn" title="搜索">🔍</button>
    </form>
  );
}
