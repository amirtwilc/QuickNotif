import React, { useState, useEffect } from 'react';
import { Bell } from 'lucide-react';
import { NotificationService, NotificationItem } from '@/services/notificationService';
import { TimeInput } from '@/components/TimeInput';
import { NameInput } from '@/components/NameInput';
import { NotificationList } from '@/components/NotificationList';
import { Button } from '@/components/ui/button';
import { ConfirmDialog } from '@/components/ConfirmDialog';
import { useToast } from '@/hooks/use-toast';

const Index = () => {
  console.log("📱 Index component rendering");
  
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [notificationName, setNotificationName] = useState('');
  const [savedNames, setSavedNames] = useState<string[]>([]);
  const [showEmptyNameDialog, setShowEmptyNameDialog] = useState(false);
  const [pendingSchedule, setPendingSchedule] = useState<{time: string, type: 'absolute' | 'relative'} | null>(null);
  const { toast } = useToast();

  const notificationService = NotificationService.getInstance();

  useEffect(() => {
    const initializeService = async () => {
      try {
        await notificationService.initialize();
        setNotifications(notificationService.getNotifications());
        setSavedNames(notificationService.getSavedNames());
      } catch (error) {
        toast({
          title: "Permission Required",
          description: "Please enable notifications to use this app.",
          variant: "destructive",
        });
      }
    };

    initializeService();

    // Check for expired notifications every 5 seconds when app is active
    const interval = setInterval(() => {
      const updatedNotifications = notificationService.getNotifications();
      setNotifications(updatedNotifications);
    }, 5000);

    return () => clearInterval(interval);
  }, []);

  const handleScheduleNotification = async (time: string, type: 'absolute' | 'relative') => {
    if (!notificationName.trim()) {
      setPendingSchedule({time, type});
      setShowEmptyNameDialog(true);
      return;
    }

    try {
      await notificationService.scheduleNotification(notificationName.trim(), time, type);
      setNotifications(notificationService.getNotifications());
      setSavedNames(notificationService.getSavedNames());
      setNotificationName('');
      
      toast({
        title: "Notification Scheduled",
        description: `"${notificationName || 'Unnamed notification'}" has been scheduled successfully.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to schedule notification.",
        variant: "destructive",
      });
    }
  };

  const handleConfirmEmptyName = async () => {
    if (pendingSchedule) {
      try {
        await notificationService.scheduleNotification('', pendingSchedule.time, pendingSchedule.type);
        setNotifications(notificationService.getNotifications());
        setSavedNames(notificationService.getSavedNames());
        setNotificationName('');
        
        toast({
          title: "Notification Scheduled",
          description: "Unnamed notification has been scheduled successfully.",
        });
      } catch (error) {
        toast({
          title: "Error",
          description: "Failed to schedule notification.",
          variant: "destructive",
        });
      }
    }
    setShowEmptyNameDialog(false);
    setPendingSchedule(null);
  };

  const handleToggleNotification = async (id: string) => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.toggleNotification(id);
      setNotifications(notificationService.getNotifications());
      
      toast({
        title: !notification?.enabled ? "Notification Disabled" : "Notification Enabled",
        description: `"${notification?.name || 'Unnamed notification'}" ${!notification?.enabled ? 'disabled' : 'enabled'}.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to toggle notification.",
        variant: "destructive",
      });
    }
  };

  const handleEditNotification = async (id: string, time: string, type: 'absolute' | 'relative') => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.updateNotificationTime(id, time, type);
      setNotifications(notificationService.getNotifications());
      
      toast({
        title: "Notification Updated",
        description: `"${notification?.name || 'Unnamed notification'}" timing has been updated.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to update notification.",
        variant: "destructive",
      });
    }
  };

  const handleDeleteNotification = async (id: string) => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.deleteNotification(id);
      setNotifications(notificationService.getNotifications());
      
      toast({
        title: "Notification Deleted",
        description: `"${notification?.name}" has been deleted.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to delete notification.",
        variant: "destructive",
      });
    }
  };

  const handleReactivateNotification = async (id: string) => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.reactivateNotification(id);
      setNotifications(notificationService.getNotifications());

      const detail = notification?.type === 'absolute'
        ? `exact time ${notification?.time}`
        : `duration ${notification?.time}`;

      toast({
        title: "Notification Rescheduled",
        description: `"${notification?.name || 'Unnamed notification'}" rescheduled using ${detail}.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to reschedule notification.",
        variant: "destructive",
      });
    }
  };

  const handleClearExpired = async () => {
    try {
      const now = Date.now();
      const expired = notifications.filter(n => n.scheduledAt.getTime() < now);

      if (expired.length === 0) {
        toast({
          title: "Nothing to clear",
          description: "No expired notifications found.",
        });
        return;
      }

      await Promise.all(expired.map(n => notificationService.deleteNotification(n.id)));
      setNotifications(notificationService.getNotifications());

      toast({
        title: "Expired cleared",
        description: `${expired.length} expired notification${expired.length > 1 ? 's' : ''} removed.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to clear expired notifications.",
        variant: "destructive",
      });
    }
  };

  return (
    <div className="min-h-screen p-4 max-w-md mx-auto">
      <header className="text-center mb-8 pt-4">
        <div className="inline-flex items-center justify-center w-16 h-16 bg-primary rounded-full mb-4">
          <Bell className="w-8 h-8 text-primary-foreground" />
        </div>
        <h1 className="text-3xl font-bold text-card-foreground mb-2">Quick Notif</h1>
        <p className="text-muted-foreground mb-3">Schedule notifications with ease</p>
      
        <NameInput
          value={notificationName}
          onChange={setNotificationName}
          savedNames={savedNames}
          placeholder="Coffee break, Meeting reminder..."
        />
      </header>

      <div className="space-y-6">

        <div className="mt-1">
          <TimeInput onSubmit={handleScheduleNotification} />
        </div>

        <div className="flex justify-end">
          <Button variant="secondary" size="sm" onClick={handleClearExpired}>
            Clear expired
          </Button>
        </div>

        <NotificationList
          notifications={notifications}
          onToggle={handleToggleNotification}
          onDelete={handleDeleteNotification}
          onEdit={handleEditNotification}
          onReactivate={handleReactivateNotification}
        />

        <ConfirmDialog
          open={showEmptyNameDialog}
          onOpenChange={setShowEmptyNameDialog}
          onConfirm={handleConfirmEmptyName}
          title="Create Unnamed Notification?"
          description="You haven't entered a name for this notification. Do you want to create it anyway?"
          confirmText="Yes"
          cancelText="No"
        />
      </div>
    </div>
  );
};

export default Index;
