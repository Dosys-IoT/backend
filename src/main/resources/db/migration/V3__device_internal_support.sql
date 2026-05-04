ALTER TABLE devices ADD COLUMN device_key VARCHAR(120);
UPDATE devices SET device_key = 'device-key-' || id WHERE device_key IS NULL;
ALTER TABLE devices ALTER COLUMN device_key SET NOT NULL;
ALTER TABLE devices ADD CONSTRAINT uq_devices_device_key UNIQUE (device_key);

ALTER TABLE devices ADD COLUMN last_seen_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE devices ADD COLUMN last_known_rtc_time TIMESTAMP WITH TIME ZONE;
ALTER TABLE devices ADD COLUMN last_known_status VARCHAR(60);
ALTER TABLE devices ADD COLUMN last_known_wifi_connected BOOLEAN;

CREATE UNIQUE INDEX ux_intake_device_schedule_scheduled_at ON intake_records(device_id, schedule_id, scheduled_at);
