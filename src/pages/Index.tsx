import { useState, useEffect, useCallback } from "react";
import { NotificationService, PermissionStep } from "@/services/notificationService";
import { NotificationItem } from "@/services/notificationService";
import { Bell } from "lucide-react";
import { TimeInput } from "@/components/TimeInput";
import { NotificationList } from "@/components/NotificationList";
import { NameInput } from "@/components/NameInput";
import { ConfirmDialog } from "@/components/ConfirmDialog";
import PermissionsDialog from "@/components/PermissionsDialog";
import { Button } from "@/components/ui/button";
import { useToast } from "@/hooks/use-toast";
import { DebugLogViewer } from '@/components/DebugLogViewer';
import notificationLogger from '@/services/notificationLogger';
import PullToRefresh from 'react-simple-pull-to-refresh';
import { App } from '@capacitor/app';
import { Capacitor, PluginListenerHandle } from '@capacitor/core';

const Index = () => {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [savedNames, setSavedNames] = useState<string[]>([]);
  const [notificationName, setNotificationName] = useState("");
  const [showEmptyNameDialog, setShowEmptyNameDialog] = useState(false);
  const [pendingSchedule, setPendingSchedule] = useState<{ time: string, type: 'absolute' | 'relative' } | null>(null);
  const [permissionStep, setPermissionStep] = useState<PermissionStep | null>(null);
  const [showPermissionDialog, setShowPermissionDialog] = useState(false);
  const { toast } = useToast();
  const [showDebug, setShowDebug] = useState(false);

  const notificationService = NotificationService.getInstance();

  const refreshData = useCallback(() => {
    setNotifications(notificationService.getNotifications());
    setSavedNames(notificationService.getSavedNames());
  }, [notificationService]);

  useEffect(() => {
    const initializeService = async () => {
      try {
        // Set up callbacks for permission flow
        notificationService.setPermissionCallbacks({
          onStepChange: (step) => {
            setPermissionStep(step);
            setShowPermissionDialog(true);
          },
          onPermissionDenied: () => {
            setShowPermissionDialog(false);
            toast({
              title: "Permissions Required",
              description: "This app requires notification permissions to function. Please enable notifications in your phone's Settings > Apps > Quick Notif > Notifications.",
              variant: "destructive",
              duration: 10000,
            });
          }
        });

        await notificationService.initialize();
        setNotifications(notificationService.getNotifications());
        setSavedNames(notificationService.getSavedNames());
      } catch (error) {
        // Error is already handled by callbacks
        // Still load any existing data
        setNotifications(notificationService.getNotifications());
        setSavedNames(notificationService.getSavedNames());
      }
    };

    initializeService();

    // Listen for app state changes (resume from background)
    // This auto-refreshes data when returning from widget interaction
    let appStateListener: PluginListenerHandle | null = null;
    if (Capacitor.isNativePlatform()) {
      appStateListener = App.addListener('appStateChange', async (state) => {
        if (state.isActive) {
          // App came to foreground - refresh data from storage
          try {
            await notificationService.refresh();
            refreshData();
          } catch (error) {
            console.error('Failed to refresh on app resume:', error);
          }
        }
      });
    }

    return () => {
      if (appStateListener) {
        appStateListener.remove();
      }
    };
  }, [notificationService, refreshData, toast]);

  const handlePermissionContinue = async () => {
    switch (permissionStep) {
      case 'notification': {
        const granted = await notificationService.requestNotificationPermission();
        if (granted) {
          await notificationService.requestAutoStartPermission();
        }
        // If not granted, the callback will handle showing error
        break;
      }

      case 'autostart':
        await notificationService.openAutoStartSettings();
        // Move to complete step after a delay
        setTimeout(async () => {
          await notificationService.completePermissionSetup();
        }, 1500);
        break;

      case 'complete':
        setShowPermissionDialog(false);
        toast({
          title: "Setup Complete!",
          description: "Your app is ready to use.",
        });
        break;
    }
  };

  const handleScheduleNotification = useCallback(async (time: string, type: 'absolute' | 'relative') => {
    if (!notificationName.trim()) {
      setPendingSchedule({ time, type });
      setShowEmptyNameDialog(true);
      return;
    }

    try {
      await notificationService.scheduleNotification(notificationName.trim(), time, type);
      refreshData();
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
  }, [notificationName, notificationService, refreshData, toast]);

  const handleConfirmEmptyName = useCallback(async () => {
    if (pendingSchedule) {
      try {
        await notificationService.scheduleNotification('', pendingSchedule.time, pendingSchedule.type);
        refreshData();
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
  }, [pendingSchedule, notificationService, refreshData, toast]);

  const handleToggleNotification = useCallback(async (id: string) => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.toggleNotification(id);
      refreshData();

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
  }, [notifications, notificationService, refreshData, toast]);

  const handleEditNotification = useCallback(async (id: string, time: string, type: 'absolute' | 'relative') => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.updateNotificationTime(id, time, type);
      refreshData();

      toast({
        title: "Notification Updated",
        description: `"${notification?.name || 'Unnamed notification'}" has been rescheduled.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to update notification.",
        variant: "destructive",
      });
    }
  }, [notifications, notificationService, refreshData, toast]);

  const handleDeleteNotification = useCallback(async (id: string) => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.deleteNotification(id);
      refreshData();

      toast({
        title: "Notification Deleted",
        description: `"${notification?.name || 'Unnamed notification'}" has been removed.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to delete notification.",
        variant: "destructive",
      });
    }
  }, [notifications, notificationService, refreshData, toast]);

  const handleReactivateNotification = useCallback(async (id: string) => {
    try {
      const notification = notifications.find(n => n.id === id);
      await notificationService.reactivateNotification(id);
      refreshData();

      toast({
        title: "Notification Reactivated",
        description: `"${notification?.name || 'Unnamed notification'}" has been rescheduled.`,
      });
    } catch (error) {
      toast({
        title: "Error",
        description: "Failed to reschedule notification.",
        variant: "destructive",
      });
    }
  }, [notifications, notificationService, refreshData, toast]);

  const handleClearExpired = useCallback(async () => {
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
      refreshData();

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
  }, [notifications, notificationService, refreshData, toast]);

  const handlePermissionSkip = async () => {
    await notificationService.completePermissionSetup();
  };

  const handleRefresh = useCallback(async () => {
    try {
      await notificationService.refresh();
      refreshData();
    } catch (error) {
      console.error('Failed to refresh notifications:', error);
    }
  }, [notificationService, refreshData]);

  return (
    <div className="min-h-screen p-4 max-w-md mx-auto">
      <PullToRefresh onRefresh={handleRefresh}>
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

          <PermissionsDialog
            open={showPermissionDialog}
            onOpenChange={setShowPermissionDialog}
            onContinue={handlePermissionContinue}
            onSkip={handlePermissionSkip}
            step={permissionStep || 'notification'}
          />

          {notificationLogger.isDebugMode() && (
            <div className="mt-6">
              <Button
                variant="outline"
                onClick={() => setShowDebug(!showDebug)}
              >
                {showDebug ? 'Hide' : 'Show'} Debug Logs
              </Button>

              {showDebug && (
                <div className="mt-4">
                  <DebugLogViewer />
                </div>
              )}
            </div>
          )}
        </div>
      </PullToRefresh>
    </div>
  );
};

export default Index;