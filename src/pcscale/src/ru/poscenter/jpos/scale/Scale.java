package ru.poscenter.jpos.scale;

import ru.poscenter.jpos.Device;
import ru.poscenter.jpos.DeviceType;

public class Scale extends Device {
	public Scale() {
		m_type = DeviceType.Scale;
	}
}
