<?php
/**
 * Fluent request-body validator. Accumulates per-field messages; the
 * caller decides when to bail (`fails()` + Response::error(..., 422, ...)`.
 *
 * Deliberately minimal — no rule DSL, no reflection, no packages. Every
 * public method returns $this so validation reads top-to-bottom in the
 * controller.
 */
final class Validator
{
    /** @var array<string,mixed> */
    private array $data;

    /** @var array<string,string> */
    private array $errors = [];

    public function __construct(array $data)
    {
        $this->data = $data;
    }

    public function required(string $field, ?string $label = null): self
    {
        $value = $this->data[$field] ?? null;
        if ($value === null || $value === '' || (is_string($value) && trim($value) === '')) {
            $this->errors[$field] = ($label ?? $field) . ' is required';
        }
        return $this;
    }

    public function email(string $field): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && $value !== '' && !filter_var($value, FILTER_VALIDATE_EMAIL)) {
            $this->errors[$field] = 'Must be a valid email address';
        }
        return $this;
    }

    public function minLength(string $field, int $min): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && mb_strlen((string) $value) < $min) {
            $this->errors[$field] = "Must be at least $min characters";
        }
        return $this;
    }

    public function maxLength(string $field, int $max): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && mb_strlen((string) $value) > $max) {
            $this->errors[$field] = "Must be at most $max characters";
        }
        return $this;
    }

    public function inEnum(string $field, array $allowed): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && !in_array($value, $allowed, true)) {
            $this->errors[$field] = 'Must be one of: ' . implode(', ', $allowed);
        }
        return $this;
    }

    public function numeric(string $field): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && !is_numeric($value)) {
            $this->errors[$field] = 'Must be a number';
        }
        return $this;
    }

    public function integer(string $field): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && filter_var($value, FILTER_VALIDATE_INT) === false) {
            $this->errors[$field] = 'Must be a whole number';
        }
        return $this;
    }

    public function inRange(string $field, float $min, float $max): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && is_numeric($value)) {
            $v = (float) $value;
            if ($v < $min || $v > $max) {
                $this->errors[$field] = "Must be between $min and $max";
            }
        }
        return $this;
    }

    public function isDatetime(string $field): self
    {
        if (isset($this->errors[$field])) return $this;
        $value = $this->data[$field] ?? null;
        if ($value !== null && $value !== '') {
            try {
                $dt = new DateTimeImmutable((string) $value);
                // MySQL DATETIME only reaches back to 1000-01-01; anything
                // before 1970 is nonsense for a workout anyway, and strict
                // mode would turn it into a 500 at insert time.
                $year = (int) $dt->format('Y');
                if ($year < 1970 || $year > 9999) {
                    $this->errors[$field] = 'Date/time out of range';
                }
            } catch (Throwable) {
                $this->errors[$field] = 'Must be a valid date/time (ISO 8601 or Y-m-d H:i:s)';
            }
        }
        return $this;
    }

    public function fails(): bool
    {
        return !empty($this->errors);
    }

    /** @return array<string,string> */
    public function errors(): array
    {
        return $this->errors;
    }
}
