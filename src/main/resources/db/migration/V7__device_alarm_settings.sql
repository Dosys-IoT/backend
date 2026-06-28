ALTER TABLE devices ADD COLUMN alarm_volume_percent INTEGER NOT NULL DEFAULT 80;
ALTER TABLE devices ADD COLUMN quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE devices ADD COLUMN quiet_hours_start VARCHAR(5) NOT NULL DEFAULT '21:00';
ALTER TABLE devices ADD COLUMN quiet_hours_end VARCHAR(5) NOT NULL DEFAULT '06:00';
ALTER TABLE devices ADD COLUMN quiet_hours_volume_percent INTEGER NOT NULL DEFAULT 50;
ALTER TABLE devices ADD COLUMN last_known_hardware_version VARCHAR(60);

ALTER TABLE device_heartbeats
    ADD COLUMN hardware_version VARCHAR(60);
