import React, { useState } from 'react';
import { NotificationItem } from '@/services/notificationService';
import { Switch } from '@/components/ui/switch';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Trash2, Clock, Edit, RefreshCw } from 'lucide-react';
import { EditTimeDialog } from '@/components/EditTimeDialog';
import { format, isToday, isTomorrow } from 'date-fns';
import { ConfirmDialog } from '@/components/ConfirmDialog';

interface NotificationListProps {
  notifications: NotificationItem[];
  onToggle: (id: string) => void;
  onDelete: (id: string) => void;
  onEdit: (id: string, time: string, type: 'absolute' | 'relative') => void;
  onReactivate: (id: string) => void;
}

export const NotificationList: React.FC<NotificationListProps> = ({
  notifications,
  onToggle,
  onDelete,
  onEdit,
  onReactivate,
}) => {
  const [editingNotification, setEditingNotification] = useState<NotificationItem | null>(null);
  const [reactivateTarget, setReactivateTarget] = useState<NotificationItem | null>(null);
  const [deleteTarget, setDeleteTarget] = useState<NotificationItem | null>(null);
  const formatTime = (date: Date) => {
    if (isToday(date)) {
      return `Today at ${format(date, 'HH:mm')}`;
    } else if (isTomorrow(date)) {
      return `Tomorrow at ${format(date, 'HH:mm')}`;
    } else {
      return format(date, 'MMM d, HH:mm');
    }
  };

  const handleEditSave = (time: string, type: 'absolute' | 'relative') => {
    if (editingNotification) {
      onEdit(editingNotification.id, time, type);
      setEditingNotification(null);
    }
  };

  if (notifications.length === 0) {
    return (
      <div className="notification-card text-center py-8">
        <Clock size={48} className="mx-auto text-muted-foreground mb-4" />
        <h3 className="text-lg font-medium text-muted-foreground mb-2">No notifications scheduled</h3>
        <p className="text-sm text-muted-foreground">Create your first notification above!</p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      <h2 className="text-xl font-semibold mb-4 flex items-center gap-2">
        <Clock size={20} />
        Scheduled Notifications
      </h2>
      
      {notifications.map((notification) => {
        const isExpired = notification.scheduledAt < new Date();
        
        return (
          <div
            key={notification.id}
            className={`notification-card ${isExpired ? 'opacity-60' : ''}`}
          >
            <div className="flex items-center justify-between">
              <div className="flex-1 min-w-0">
                <div className="flex items-center gap-2 mb-2">
                  <h3 className="font-medium text-card-foreground truncate">
                    {notification.name || 'Unnamed notification'}
                  </h3>
                  {isExpired && (
                    <Badge variant="destructive" className="text-xs">
                      Expired
                    </Badge>
                  )}
                </div>
                
                <div className="space-y-0 text-sm leading-tight text-muted-foreground">
                  <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                      <span className="text-xs">Last updated:</span>
                      <span>{formatTime(notification.updatedAt)}</span>
                    </div>
                    <Switch
                      checked={notification.enabled && !isExpired}
                      onCheckedChange={() => onToggle(notification.id)}
                      disabled={isExpired}
                    />
                  </div>
                  
                  <div className="flex items-center justify-between gap-4">
                    <div className="flex items-center gap-2">
                      <span className="text-xs">Finish:</span>
                      <span className={isExpired ? 'text-destructive' : ''}>
                        {formatTime(notification.scheduledAt)}
                      </span>
                    </div>
                    <div className="flex items-center gap-2">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => setEditingNotification(notification)}
                        className="h-7 w-7 text-primary hover:text-primary hover:bg-primary/10"
                      >
                        <Edit size={16} />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => {
                          const now = new Date();
                          if (!isExpired && notification.scheduledAt > now) {
                            setDeleteTarget(notification);
                          } else {
                            onDelete(notification.id);
                          }
                        }}
                        className="h-7 w-7 text-destructive hover:text-destructive hover:bg-destructive/10"
                      >
                        <Trash2 size={16} />
                      </Button>
                    </div>
                  </div>
                  
                  <div className="flex items-center justify-between gap-4 -mt-2">
                    <div className="text-xs">
                      Last set: {notification.type === 'absolute' ? `${notification.time} (exact)` : `${notification.time} (duration)`}
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => {
                        const now = new Date();
                        if (notification.enabled && notification.scheduledAt > now) {
                          setReactivateTarget(notification);
                        } else {
                          onReactivate(notification.id);
                        }
                      }}
                      className="h-7 w-7 text-primary hover:text-primary hover:bg-primary/10"
                      aria-label="Reactivate"
                    >
                      <RefreshCw size={16} />
                    </Button>
                  </div>
                </div>
              </div>
            </div>
          </div>
        );
      })}

      {editingNotification && (
        <EditTimeDialog
          open={!!editingNotification}
          onOpenChange={(open) => !open && setEditingNotification(null)}
          onSave={handleEditSave}
          notificationName={editingNotification.name}
          currentTime={editingNotification.time}
          currentType={editingNotification.type}
        />
      )}

      {reactivateTarget && (
        <ConfirmDialog
          open={!!reactivateTarget}
          onOpenChange={(open) => !open && setReactivateTarget(null)}
          onConfirm={() => {
            onReactivate(reactivateTarget.id);
            setReactivateTarget(null);
          }}
          title="Reschedule notification?"
          description={`"${reactivateTarget.name || 'Unnamed notification'}" is active. Reschedule to the same ${reactivateTarget.type === 'absolute' ? `time (${reactivateTarget.time})` : `duration (${reactivateTarget.time})`}?`}
          confirmText="Reschedule"
          cancelText="Cancel"
        />
      )}

      {deleteTarget && (
        <ConfirmDialog
          open={!!deleteTarget}
          onOpenChange={(open) => !open && setDeleteTarget(null)}
          onConfirm={() => {
            onDelete(deleteTarget.id);
            setDeleteTarget(null);
          }}
          title="Delete notification?"
          description={`Are you sure you want to delete "${deleteTarget.name || 'Unnamed notification'}"? This action cannot be undone.`}
          confirmText="Delete"
          cancelText="Cancel"
        />
      )}
    </div>
  );
};