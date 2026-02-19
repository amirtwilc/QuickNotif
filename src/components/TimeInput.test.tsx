import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TimeInput } from './TimeInput';

describe('TimeInput', () => {
  it('renders with Duration (relative) tab active by default', () => {
    render(<TimeInput onSubmit={vi.fn()} />);
    // The relative tab content should be visible (Hours/Minutes labels)
    expect(screen.getByLabelText('Hours')).toBeInTheDocument();
    expect(screen.getByLabelText('Minutes')).toBeInTheDocument();
  });

  it('switching to "Exact Time" tab shows time input', async () => {
    const user = userEvent.setup();
    render(<TimeInput onSubmit={vi.fn()} />);
    await user.click(screen.getByRole('tab', { name: /Exact Time/i }));
    expect(screen.getByLabelText(/Set specific time/i)).toBeInTheDocument();
  });

  describe('absolute mode', () => {
    it('Schedule button is disabled with no input', async () => {
      const user = userEvent.setup();
      render(<TimeInput onSubmit={vi.fn()} />);
      await user.click(screen.getByRole('tab', { name: /Exact Time/i }));
      const button = screen.getByRole('button', { name: /Schedule for/i });
      expect(button).toBeDisabled();
    });

    it('calls onSubmit with time and "absolute" after filling and clicking', async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn();
      render(<TimeInput onSubmit={onSubmit} />);
      await user.click(screen.getByRole('tab', { name: /Exact Time/i }));
      const input = screen.getByLabelText(/Set specific time/i);
      await user.type(input, '14:30');
      const button = screen.getByRole('button', { name: /Schedule for/i });
      await user.click(button);
      expect(onSubmit).toHaveBeenCalledWith('14:30', 'absolute');
    });

    it('resets the time field after submission', async () => {
      const user = userEvent.setup();
      render(<TimeInput onSubmit={vi.fn()} />);
      await user.click(screen.getByRole('tab', { name: /Exact Time/i }));
      const input = screen.getByLabelText(/Set specific time/i) as HTMLInputElement;
      await user.type(input, '14:30');
      await user.click(screen.getByRole('button', { name: /Schedule for/i }));
      expect(input.value).toBe('');
    });
  });

  describe('relative mode', () => {
    it('Schedule button is disabled when both hours and minutes are empty', () => {
      render(<TimeInput onSubmit={vi.fn()} />);
      const button = screen.getByRole('button', { name: /Schedule in/i });
      expect(button).toBeDisabled();
    });

    it('calls onSubmit with "30 minutes" and "relative" when only minutes filled', async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn();
      render(<TimeInput onSubmit={onSubmit} />);
      await user.clear(screen.getByLabelText('Minutes'));
      await user.type(screen.getByLabelText('Minutes'), '30');
      await user.click(screen.getByRole('button', { name: /Schedule in/i }));
      expect(onSubmit).toHaveBeenCalledWith('30 minutes', 'relative');
    });

    it('uses singular "minute" for value of 1', async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn();
      render(<TimeInput onSubmit={onSubmit} />);
      await user.type(screen.getByLabelText('Minutes'), '1');
      await user.click(screen.getByRole('button', { name: /Schedule in/i }));
      expect(onSubmit).toHaveBeenCalledWith('1 minute', 'relative');
    });

    it('uses singular "hour" for value of 1', async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn();
      render(<TimeInput onSubmit={onSubmit} />);
      await user.type(screen.getByLabelText('Hours'), '1');
      await user.click(screen.getByRole('button', { name: /Schedule in/i }));
      expect(onSubmit).toHaveBeenCalledWith('1 hour', 'relative');
    });

    it('uses plural "hours" for values > 1', async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn();
      render(<TimeInput onSubmit={onSubmit} />);
      await user.type(screen.getByLabelText('Hours'), '2');
      await user.click(screen.getByRole('button', { name: /Schedule in/i }));
      expect(onSubmit).toHaveBeenCalledWith('2 hours', 'relative');
    });

    it('combines hours and minutes correctly', async () => {
      const user = userEvent.setup();
      const onSubmit = vi.fn();
      render(<TimeInput onSubmit={onSubmit} />);
      await user.type(screen.getByLabelText('Hours'), '1');
      await user.type(screen.getByLabelText('Minutes'), '30');
      await user.click(screen.getByRole('button', { name: /Schedule in/i }));
      expect(onSubmit).toHaveBeenCalledWith('1 hour 30 minutes', 'relative');
    });

    it('resets fields after submission', async () => {
      const user = userEvent.setup();
      render(<TimeInput onSubmit={vi.fn()} />);
      const hoursInput = screen.getByLabelText('Hours') as HTMLInputElement;
      const minutesInput = screen.getByLabelText('Minutes') as HTMLInputElement;
      await user.type(hoursInput, '2');
      await user.type(minutesInput, '15');
      await user.click(screen.getByRole('button', { name: /Schedule in/i }));
      expect(hoursInput.value).toBe('');
      expect(minutesInput.value).toBe('');
    });
  });
});
