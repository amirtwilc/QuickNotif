import React, { useState, useRef, useEffect } from 'react';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { X } from 'lucide-react';

interface NameInputProps {
  value: string;
  onChange: (value: string) => void;
  savedNames: string[];
  placeholder?: string;
}

export const NameInput: React.FC<NameInputProps> = ({ 
  value, 
  onChange, 
  savedNames,
  placeholder = "Enter notification name..."
}) => {
  const [showSuggestions, setShowSuggestions] = useState(false);
  const [filteredNames, setFilteredNames] = useState<string[]>([]);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (value) {
      const filtered = savedNames.filter(name => 
        name.toLowerCase().includes(value.toLowerCase()) && name !== value
      );
      setFilteredNames(filtered);
      setShowSuggestions(filtered.length > 0);
    } else {
      setFilteredNames(savedNames.slice(0, 5));
      setShowSuggestions(savedNames.length > 0);
    }
  }, [value, savedNames]);

  const handleInputFocus = () => {
    setShowSuggestions(savedNames.length > 0);
  };

  const handleInputBlur = () => {
    // Delay hiding suggestions to allow for click events
    setTimeout(() => setShowSuggestions(false), 150);
  };

  const selectName = (name: string) => {
    onChange(name);
    setShowSuggestions(false);
    inputRef.current?.focus();
  };

  return (
    <div className="relative mb-4">
      <Label htmlFor="notification-name" className="text-sm font-medium text-muted-foreground">
        Notification Name
      </Label>
      <div className="relative">
        <Input
          ref={inputRef}
          id="notification-name"
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onFocus={handleInputFocus}
          onBlur={handleInputBlur}
          placeholder={placeholder}
          className="time-input mt-0.5 pr-8"
        />
        {value && (
          <button
            type="button"
            onClick={() => {
              onChange('');
              inputRef.current?.focus();
            }}
            className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground transition-colors mt-0.25"
            aria-label="Clear input"
          >
            <X size={16} />
          </button>
        )}
      </div>
      
      {showSuggestions && (
        <div className="absolute top-full left-0 right-0 mt-1 bg-card border border-border rounded-lg shadow-medium z-10 max-h-40 overflow-y-auto">
          {filteredNames.length > 0 ? (
            <div className="p-1.5">
              <div className="text-xs text-muted-foreground mb-1.5 px-2">Recent names:</div>
              <div className="flex flex-wrap gap-1">
                {filteredNames.map((name, index) => (
                  <Badge
                    key={index}
                    variant="secondary"
                    className="cursor-pointer hover:bg-primary hover:text-primary-foreground transition-colors"
                    onClick={() => selectName(name)}
                  >
                    {name}
                  </Badge>
                ))}
              </div>
            </div>
          ) : value && (
            <div className="p-3 text-sm text-muted-foreground text-center">
              No matching saved names
            </div>
          )}
        </div>
      )}
    </div>
  );
};