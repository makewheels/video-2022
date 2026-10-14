import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type OAuthApp } from '../api/client';




export function useAppsPage() {
  const queryClient = useQueryClient();
  const [showForm, setShowForm] = useState(false);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [redirectUris, setRedirectUris] = useState('');
  const [scopes, setScopes] = useState<string[]>([]);
  const [newApp, setNewApp] = useState<OAuthApp | null>(null);

  const { data: apps, isLoading } = useQuery({
    queryKey: ['apps'],
    queryFn: () => apiClient.listApps(),
  });

  const createMutation = useMutation({
    mutationFn: (data: { name: string; description: string; redirectUris: string[]; scopes: string[] }) =>
      apiClient.createApp(data),
    onSuccess: (app) => {
      setNewApp(app);
      setShowForm(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['apps'] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => apiClient.deleteApp(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['apps'] });
    },
  });

  const resetForm = () => {
    setName('');
    setDescription('');
    setRedirectUris('');
    setScopes([]);
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    const uris = redirectUris
      .split('\n')
      .map((u) => u.trim())
      .filter(Boolean);
    createMutation.mutate({ name, description, redirectUris: uris, scopes });
  };

  const toggleScope = (scope: string) => {
    setScopes((prev) => (prev.includes(scope) ? prev.filter((s) => s !== scope) : [...prev, scope]));
  };

  const handleDelete = (id: string) => {
    if (confirm('确定要删除这个应用吗？此操作不可撤销。')) {
      deleteMutation.mutate(id);
    }
  };

  return {
    showForm,
    setShowForm,
    name,
    setName,
    description,
    setDescription,
    redirectUris,
    setRedirectUris,
    scopes,
    newApp,
    setNewApp,
    apps,
    isLoading,
    createMutation,
    handleSubmit,
    toggleScope,
    handleDelete,
  };
}
