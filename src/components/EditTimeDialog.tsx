import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { TimeInput } from '@/components/TimeInput';

interface EditTimeDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (time: string, type: 'absolute' | 'relative') => void;
  notificationName: string;
  currentTime: string;
  currentType: 'absolute' | 'relative';
}

export const EditTimeDialog: React.FC<EditTimeDialogProps> = ({
  open,
  onOpenChange,
  onSave,
  notificationName,
  currentTime,
  currentType
}) => {
  const [pendingTime, setPendingTime] = useState<string>('');
  const [pendingType, setPendingType] = useState<'absolute' | 'relative'>('absolute');

  useEffect(() => {
    if (open) {
      setPendingTime(currentTime);
      setPendingType(currentType);
    }
  }, [open, currentTime, currentType]);

  const handleSubmit = (time: string, type: 'absolute' | 'relative') => {
    setPendingTime(time);
    setPendingType(type);
    onSave(time, type);
    onOpenChange(false);
  };

  const handleCancel = () => {
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[425px]">
        <DialogHeader>
          <DialogTitle>Edit Notification Time</DialogTitle>
          <DialogDescription>
            Update the timing for "{notificationName || 'Unnamed notification'}"
          </DialogDescription>
        </DialogHeader>
        
        <div className="py-4">
          <TimeInput onSubmit={handleSubmit} />
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel}>
            Cancel
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};