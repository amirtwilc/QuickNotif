import {
  AlertDialog,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Bell, Smartphone } from "lucide-react";

interface PermissionsDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onContinue: () => void;
  onSkip?: () => void;
  step: 'notification' | 'autostart' | 'complete';
}

const PermissionsDialog = ({ open, onOpenChange, onContinue, onSkip, step }: PermissionsDialogProps) => {
  const getStepContent = () => {
    switch (step) {
      case 'notification':
        return {
          icon: <Bell className="w-12 h-12 text-primary" />,
          title: "Enable Notifications",
          description: "Quick Notif needs permission to send you notifications. You'll be prompted to allow this next.",
          buttonText: "Continue"
        };
      
      case 'autostart':
        return {
          icon: <Smartphone className="w-12 h-12 text-primary" />,
          title: "Background Auto-start",
          description: (
            <div className="space-y-3 text-left">
              <p>Some phones require permission for apps to start in the background.</p>
              <div className="bg-muted p-3 rounded-md">
                <p className="font-semibold mb-2">If a settings page opens:</p>
                <ol className="list-decimal list-inside space-y-1 text-sm">
                  <li>Find "Quick Notif" in the app list</li>
                  <li>Toggle it ON or enable it</li>
                  <li>Go back to the app</li>
                </ol>
              </div>
              <div className="bg-amber-50 dark:bg-amber-900/20 p-3 rounded-md border border-amber-200 dark:border-amber-800">
                <p className="text-sm text-amber-900 dark:text-amber-100">
                  <span className="font-semibold">Note:</span> This setting is only available on certain phones (Xiaomi, OPPO, Vivo, Huawei, OnePlus, ASUS). If nothing opens or you have a different phone, tap "Skip" - your app will still work.
                </p>
              </div>
            </div>
          ),
          buttonText: "Try to Open Auto-start Settings",
          skipButton: true
        };
      
      case 'complete':
        return {
          icon: <Bell className="w-12 h-12 text-green-500" />,
          title: "Setup Complete!",
          description: "Your app is now configured for reliable notifications. You can start creating notification timers.",
          buttonText: "Start Using App"
        };
    }
  };

  const content = getStepContent();

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent className="max-w-md">
        <AlertDialogHeader>
          <div className="flex justify-center mb-4">
            {content.icon}
          </div>
          <AlertDialogTitle className="text-center">{content.title}</AlertDialogTitle>
          <AlertDialogDescription asChild>
            <div className="text-center">
              {content.description}
            </div>
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter className="flex-col sm:flex-col gap-2">
          <Button onClick={onContinue} className="w-full">
            {content.buttonText}
          </Button>
          {content.skipButton && (
            <Button onClick={onSkip} variant="outline" className="w-full">
              Skip This Step
            </Button>
          )}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
};

export default PermissionsDialog;