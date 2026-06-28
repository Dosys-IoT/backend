ALTER TABLE devices ADD COLUMN hardware_device_id BIGINT;
ALTER TABLE devices ADD CONSTRAINT uq_devices_hardware_device_id UNIQUE (hardware_device_id);
