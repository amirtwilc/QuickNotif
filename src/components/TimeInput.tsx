import React, { useState, useRef } from 'react';
import { z } from 'zod';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { Clock, Timer } from 'lucide-react';

const relativeSchema = z.object({
  hours: z.number().int().min(0).max(8760),
  minutes: z.number().int().min(0).max(59),
}).refine(d => d.hours > 0 || d.minutes > 0, { message: "At least 1 minute required" });

interface TimeInputProps {
  onSubmit: (time: string, type: 'absolute' | 'relative') => void;
}

export const TimeInput: React.FC<TimeInputProps> = ({ onSubmit }) => {
  const [absoluteTime, setAbsoluteTime] = useState('');
  const [hours, setHours] = useState('');
  const [minutes, setMinutes] = useState('');
  const [activeTab, setActiveTab] = useState('relative');
  const hoursRef = useRef<HTMLInputElement>(null);
  const minutesRef = useRef<HTMLInputElement>(null);

  const handleAbsoluteSubmit = () => {
    if (absoluteTime) {
      onSubmit(absoluteTime, 'absolute');
      setAbsoluteTime('');
    }
  };

  const handleRelativeSubmit = () => {
    const parsed = relativeSchema.safeParse({
      hours: hours ? parseInt(hours, 10) : 0,
      minutes: minutes ? parseInt(minutes, 10) : 0,
    });
    if (!parsed.success) return;

    const { hours: h, minutes: m } = parsed.data;
    const parts = [];
    if (h > 0) parts.push(`${h} ${h === 1 ? 'hour' : 'hours'}`);
    if (m > 0) parts.push(`${m} ${m === 1 ? 'minute' : 'minutes'}`);

    onSubmit(parts.join(' '), 'relative');
    setHours('');
    setMinutes('');
  };

  return (
    <div className="notification-card mb-6">
      <Tabs value={activeTab} onValueChange={setActiveTab} className="w-full">
        <TabsList className="grid w-full grid-cols-2 mb-4">
          <TabsTrigger value="relative" className="flex items-center gap-2">
            <Timer size={16} />
            Duration
          </TabsTrigger>
          <TabsTrigger value="absolute" className="flex items-center gap-2">
            <Clock size={16} />
            Exact Time
          </TabsTrigger>
        </TabsList>
        
        <TabsContent value="absolute" className="space-y-4">
          <div>
            <Label htmlFor="absolute-time" className="text-sm font-medium text-muted-foreground">
              Set specific time (24-hour format)
            </Label>
            <Input
              id="absolute-time"
              type="time"
              value={absoluteTime}
              onChange={(e) => setAbsoluteTime(e.target.value)}
              className="time-input mt-2"
            />
          </div>
          <Button 
            onClick={handleAbsoluteSubmit}
            className="btn-primary w-full"
            disabled={!absoluteTime}
          >
            Schedule for {absoluteTime || 'Selected Time'}
          </Button>
        </TabsContent>
        
        <TabsContent value="relative" className="space-y-4">
          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label htmlFor="hours" className="text-sm font-medium text-muted-foreground">
                Hours
              </Label>
              <Input
                id="hours"
                ref={hoursRef}
                type="number"
                inputMode="numeric"
                min="0"
                max="99"
                value={hours}
                onChange={(e) => {
                  const v = e.target.value.replace(/[^0-9]/g, '').slice(0, 2);
                  setHours(v);
                  if (v.length >= 2) {
                    minutesRef.current?.focus();
                  }
                }}
                placeholder="0"
                className="time-input mt-2"
              />
            </div>
            <div>
              <Label htmlFor="minutes" className="text-sm font-medium text-muted-foreground">
                Minutes
              </Label>
              <Input
                id="minutes"
                ref={minutesRef}
                type="number"
                inputMode="numeric"
                min="1"
                max="59"
                value={minutes}
                onChange={(e) => setMinutes(e.target.value.replace(/[^0-9]/g, '').slice(0, 2))}
                placeholder="15"
                className="time-input mt-2"
              />
            </div>
          </div>
          <Button 
            onClick={handleRelativeSubmit}
            className="btn-primary w-full"
            disabled={!hours && !minutes}
          >
            Schedule in {
              [
                hours && parseInt(hours) > 0 ? `${parseInt(hours)} ${parseInt(hours) === 1 ? 'hour' : 'hours'}` : '',
                minutes && parseInt(minutes) > 0 ? `${parseInt(minutes)} ${parseInt(minutes) === 1 ? 'minute' : 'minutes'}` : ''
              ].filter(Boolean).join(' ') || 'Selected Duration'
            }
          </Button>
        </TabsContent>
      </Tabs>
    </div>
  );
};