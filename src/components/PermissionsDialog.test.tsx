import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import PermissionsDialog from './PermissionsDialog';

function renderDialog(
  step: 'notification' | 'autostart' | 'complete',
  { onContinue = vi.fn(), onSkip = vi.fn() } = {}
) {
  return render(
    <PermissionsDialog
      open={true}
      onOpenChange={vi.fn()}
      onContinue={onContinue}
      onSkip={onSkip}
      step={step}
    />
  );
}

describe('PermissionsDialog', () => {
  it('step="notification" shows "Enable Notifications" heading', () => {
    renderDialog('notification');
    expect(screen.getByText('Enable Notifications')).toBeInTheDocument();
  });

  it('step="notification" shows a Continue button', () => {
    renderDialog('notification');
    expect(screen.getByRole('button', { name: 'Continue' })).toBeInTheDocument();
  });

  it('step="autostart" shows "Skip This Step" button', () => {
    renderDialog('autostart');
    expect(screen.getByRole('button', { name: 'Skip This Step' })).toBeInTheDocument();
  });

  it('step="notification" does NOT show "Skip This Step" button', () => {
    renderDialog('notification');
    expect(screen.queryByRole('button', { name: 'Skip This Step' })).not.toBeInTheDocument();
  });

  it('step="complete" shows "Setup Complete!" heading', () => {
    renderDialog('complete');
    expect(screen.getByText('Setup Complete!')).toBeInTheDocument();
  });

  it('step="complete" shows "Start Using App" button', () => {
    renderDialog('complete');
    expect(screen.getByRole('button', { name: 'Start Using App' })).toBeInTheDocument();
  });

  it('step="complete" does NOT show "Skip This Step" button', () => {
    renderDialog('complete');
    expect(screen.queryByRole('button', { name: 'Skip This Step' })).not.toBeInTheDocument();
  });

  it('skip button calls onSkip, not onContinue', async () => {
    const onContinue = vi.fn();
    const onSkip = vi.fn();
    renderDialog('autostart', { onContinue, onSkip });
    await userEvent.click(screen.getByRole('button', { name: 'Skip This Step' }));
    expect(onSkip).toHaveBeenCalledOnce();
    expect(onContinue).not.toHaveBeenCalled();
  });
});
