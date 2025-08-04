ALTER TABLE mhu_muutos
    ALTER COLUMN validi_aikana
        SET DEFAULT tstzrange(CURRENT_TIMESTAMP, 'infinity');
