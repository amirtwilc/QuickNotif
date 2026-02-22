import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Share2, Trash2, Download, RefreshCw, Info } from 'lucide-react';
import { Share } from '@capacitor/share';
import { Capacitor } from '@capacitor/core';
import notificationLogger from '@/services/notificationLogger';
import { toast } from '@/hooks/use-toast';

interface LogStatistics {
  totalEntries: number;
  schedules: number;
  verifications: number;
  fires: number;
  deletes: number;
  errors: number;
  systemChecks: number;
  verificationFailures: number;
  orphanedNotifications: number;
}

export const DebugLogViewer: React.FC = () => {
  const [logContent, setLogContent] = useState<string>('');
  const [stats, setStats] = useState<LogStatistics | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    loadLog();
  }, []);

  const loadLog = async () => {
    setLoading(true);
    try {
      const content = await notificationLogger.getLogFile();
      setLogContent(content);

      const statistics = await notificationLogger.getStatistics();
      setStats(statistics);
    } catch (e) {
      toast({
        title: "Error",
        description: "Failed to load log file",
        variant: "destructive"
      });
    }
    setLoading(false);
  };

  const handleShare = async () => {
    if (!Capacitor.isNativePlatform()) {
      toast({
        title: "Not Available",
        description: "Sharing only available on mobile",
        variant: "destructive"
      });
      return;
    }

    try {
      const uri = await notificationLogger.getLogFileUri();
      
      await Share.share({
        title: 'Quick Notif Debug Log',
        text: 'Debug log from Quick Notif app',
        url: uri,
        dialogTitle: 'Share Debug Log'
      });
    } catch (e) {
      toast({
        title: "Error",
        description: "Failed to share log file",
        variant: "destructive"
      });
    }
  };

  const handleClear = async () => {
    if (confirm('Are you sure you want to clear the debug log?')) {
      await notificationLogger.clearLog();
      await loadLog();
      
      toast({
        title: "Log Cleared",
        description: "Debug log has been cleared"
      });
    }
  };

  const handleDownload = () => {
    // Create a blob and download
    const blob = new Blob([logContent], { type: 'text/plain' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `notification_debug_${new Date().toISOString().split('T')[0]}.log`;
    a.click();
    URL.revokeObjectURL(url);
  };

  if (!notificationLogger.isDebugMode()) {
    return (
      <Card>
        <CardHeader>
          <CardTitle>Debug Logging</CardTitle>
          <CardDescription>Debug mode is currently disabled</CardDescription>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">
            To enable debug logging, set DEBUG_MODE = true in notificationLogger.ts
          </p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {/* Statistics Card */}
      {stats && (
        <Card>
          <CardHeader>
            <CardTitle className="flex items-center gap-2">
              <Info size={20} />
              Log Statistics
            </CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold">{stats.totalEntries}</div>
                <div className="text-xs text-muted-foreground">Total Entries</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-blue-600">{stats.schedules}</div>
                <div className="text-xs text-muted-foreground">Scheduled</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-green-600">{stats.verifications}</div>
                <div className="text-xs text-muted-foreground">Verified</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-purple-600">{stats.systemChecks}</div>
                <div className="text-xs text-muted-foreground">System Checks</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-orange-600">{stats.fires}</div>
                <div className="text-xs text-muted-foreground">Fired</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-gray-600">{stats.deletes}</div>
                <div className="text-xs text-muted-foreground">Deleted</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-red-600">{stats.errors}</div>
                <div className="text-xs text-muted-foreground">Errors</div>
              </div>
              
              <div className="text-center p-3 bg-secondary rounded-lg">
                <div className="text-2xl font-bold text-yellow-600">{stats.verificationFailures}</div>
                <div className="text-xs text-muted-foreground">Verify Failed</div>
              </div>
            </div>
          </CardContent>
        </Card>
      )}

      {/* Log Viewer Card */}
      <Card>
        <CardHeader>
          <div className="flex items-center justify-between">
            <div>
              <CardTitle>Debug Log</CardTitle>
              <CardDescription>Real-time notification debugging</CardDescription>
            </div>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={loadLog} disabled={loading}>
                <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
              </Button>
              
              <Button 
                variant="outline" 
                size="sm" 
                onClick={async () => {
                  await notificationLogger.triggerSystemCheck();
                  setTimeout(() => loadLog(), 500); // Refresh after check
                  toast({
                    title: "System Check Complete",
                    description: "Check the logs for results"
                  });
                }}
                title="Run system check now"
              >
                🔍
              </Button>
              
              {Capacitor.isNativePlatform() ? (
                <Button variant="outline" size="sm" onClick={handleShare}>
                  <Share2 size={16} />
                </Button>
              ) : (
                <Button variant="outline" size="sm" onClick={handleDownload}>
                  <Download size={16} />
                </Button>
              )}
              
              <Button variant="outline" size="sm" onClick={handleClear}>
                <Trash2 size={16} />
              </Button>
            </div>
          </div>
        </CardHeader>
        <CardContent>
          <div className="space-y-2">
            <div className="bg-black text-green-400 p-4 rounded-lg font-mono text-xs overflow-x-auto max-h-96 overflow-y-auto">
              <pre className="whitespace-pre-wrap">{logContent || 'No log entries yet...'}</pre>
            </div>

            <p className="text-xs text-muted-foreground mt-2">
              Log rotates automatically at 10,000 lines.
            </p>
          </div>
        </CardContent>
      </Card>
    </div>
  );
};