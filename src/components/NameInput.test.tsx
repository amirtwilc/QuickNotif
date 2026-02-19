import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NameInput } from './NameInput';

function renderNameInput(
  value = '',
  savedNames: string[] = [],
  onChange = vi.fn()
) {
  return render(
    <NameInput value={value} onChange={onChange} savedNames={savedNames} />
  );
}

describe('NameInput', () => {
  it('shows no suggestions on focus when savedNames is empty', async () => {
    const user = userEvent.setup();
    renderNameInput('', []);
    await user.click(screen.getByRole('textbox'));
    expect(screen.queryByText('Recent names:')).not.toBeInTheDocument();
  });

  it('shows up to 5 suggestions on focus when value is empty', async () => {
    const user = userEvent.setup();
    const names = ['Alpha', 'Beta', 'Gamma', 'Delta', 'Epsilon', 'Zeta'];
    renderNameInput('', names);
    await user.click(screen.getByRole('textbox'));
    // Should show max 5
    expect(screen.getByText('Alpha')).toBeInTheDocument();
    expect(screen.getByText('Epsilon')).toBeInTheDocument();
    expect(screen.queryByText('Zeta')).not.toBeInTheDocument();
  });

  it('filters suggestions case-insensitively when value is typed', async () => {
    const names = ['Meeting', 'Lunch', 'Meeting reminder'];
    renderNameInput('meet', names);
    // The effect runs on value change; suggestions should filter
    // Simulate focus to trigger show
    const input = screen.getByRole('textbox');
    await userEvent.click(input);
    expect(screen.getByText('Meeting')).toBeInTheDocument();
    expect(screen.getByText('Meeting reminder')).toBeInTheDocument();
    expect(screen.queryByText('Lunch')).not.toBeInTheDocument();
  });

  it('clicking a suggestion calls onChange with the name', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(
      <NameInput value="" onChange={onChange} savedNames={['Daily standup']} />
    );
    await user.click(screen.getByRole('textbox'));
    await user.click(screen.getByText('Daily standup'));
    expect(onChange).toHaveBeenCalledWith('Daily standup');
  });

  it('clear button appears when value is non-empty', () => {
    renderNameInput('Some text');
    expect(screen.getByRole('button', { name: 'Clear input' })).toBeInTheDocument();
  });

  it('clear button is NOT shown when value is empty', () => {
    renderNameInput('');
    expect(screen.queryByRole('button', { name: 'Clear input' })).not.toBeInTheDocument();
  });

  it('clicking the clear button calls onChange with empty string', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<NameInput value="Some text" onChange={onChange} savedNames={[]} />);
    await user.click(screen.getByRole('button', { name: 'Clear input' }));
    expect(onChange).toHaveBeenCalledWith('');
  });

  it('mousedown outside the container hides suggestions', async () => {
    const user = userEvent.setup();
    render(
      <div>
        <NameInput value="" onChange={vi.fn()} savedNames={['Alpha']} />
        <button>Outside</button>
      </div>
    );
    // Open suggestions
    await user.click(screen.getByRole('textbox'));
    expect(screen.getByText('Alpha')).toBeInTheDocument();
    // Click outside
    fireEvent.mouseDown(screen.getByRole('button', { name: 'Outside' }));
    expect(screen.queryByText('Alpha')).not.toBeInTheDocument();
  });
});
