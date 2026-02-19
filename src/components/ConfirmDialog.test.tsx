import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmDialog } from './ConfirmDialog';

function renderDialog(overrides: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
  const props = {
    open: true,
    onOpenChange: vi.fn(),
    onConfirm: vi.fn(),
    title: 'Are you sure?',
    description: 'This action cannot be undone.',
    confirmText: 'Yes',
    cancelText: 'No',
    ...overrides,
  };
  return { ...render(<ConfirmDialog {...props} />), props };
}

describe('ConfirmDialog', () => {
  it('renders title and description when open=true', () => {
    renderDialog();
    expect(screen.getByText('Are you sure?')).toBeInTheDocument();
    expect(screen.getByText('This action cannot be undone.')).toBeInTheDocument();
  });

  it('calls onConfirm when confirm button is clicked', async () => {
    const user = userEvent.setup();
    const { props } = renderDialog({ confirmText: 'Delete' });
    await user.click(screen.getByRole('button', { name: 'Delete' }));
    expect(props.onConfirm).toHaveBeenCalledTimes(1);
  });

  it('does NOT call onConfirm when cancel button is clicked', async () => {
    const user = userEvent.setup();
    const { props } = renderDialog({ cancelText: 'Cancel' });
    await user.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(props.onConfirm).not.toHaveBeenCalled();
  });

  it('renders with custom confirmText and cancelText', () => {
    renderDialog({ confirmText: 'Remove', cancelText: 'Keep' });
    expect(screen.getByRole('button', { name: 'Remove' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Keep' })).toBeInTheDocument();
  });
});
