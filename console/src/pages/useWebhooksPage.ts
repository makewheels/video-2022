import { useState, type FormEvent } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { apiClient, type WebhookConfig } from '../api/client';
import { useSearchParams } from 'react-router-dom';

export function useWebhooksPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const [selectedAppId, setSelectedAppId] = useState(searchParams.get('appId') || '');
  const [showForm, setShowForm] = useState(false);
  const [url, setUrl] = useState('');
  const [events, setEvents] = useState<string[]>([]);

  const { data: apps, isLoading: appsLoading } = useQuery({
    queryKey: ['apps'],
    queryFn: () => apiClient.listApps(),
  });

  const {
    data: webhooks,
    isLoading: webhooksLoading,
    error: webhooksError,
  } = useQuery({
    queryKey: ['webhooks', selectedAppId],
    queryFn: () => apiClient.listWebhooks(selectedAppId),
    enabled: !!selectedAppId,
    retry: false,
  });

  const createMutation = useMutation({
    mutationFn: (data: { url: string; events: string[] }) =>
      apiClient.createWebhook(selectedAppId, data),
    onSuccess: () => {
      setShowForm(false);
      resetForm();
      queryClient.invalidateQueries({ queryKey: ['webhooks', selectedAppId] });
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (webhookId: string) =>
      apiClient.deleteWebhook(selectedAppId, webhookId),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['webhooks', selectedAppId] });
    },
  });

  const resetForm = () => {
    setUrl('');
    setEvents([]);
  };

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    createMutation.mutate({ url, events });
  };

  const toggleEvent = (event: string) => {
    setEvents((prev) =>
      prev.includes(event) ? prev.filter((e) => e !== event) : [...prev, event]
    );
  };

  const handleDelete = (webhook: WebhookConfig) => {
    if (confirm(`确定要删除 Webhook (${webhook.url}) 吗？`)) {
      deleteMutation.mutate(webhook.id);
    }
  };

  return {
    selectedAppId,
    setSelectedAppId,
    setSearchParams,
    showForm,
    setShowForm,
    url,
    setUrl,
    events,
    apps,
    appsLoading,
    webhooks,
    webhooksLoading,
    webhooksError,
    createMutation,
    deleteMutation,
    handleSubmit,
    toggleEvent,
    handleDelete,
  };
}
