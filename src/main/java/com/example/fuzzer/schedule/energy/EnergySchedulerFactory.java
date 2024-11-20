package com.example.fuzzer.schedule.energy;

/**
 * 能量调度器工厂类
 */
public class EnergySchedulerFactory {

    /**
     * 创建能量调度器
     *
     * @param type 调度器类型
     * @return 能量调度器实例
     */
    public static EnergyScheduler createEnergyScheduler(EnergyScheduler.Type type) {
        EnergyScheduler scheduler = null;
        switch (type) {
            case BASIC:
                scheduler = new BasicEnergyScheduler();
                break;
            case COVERAGE_BASED:
                scheduler = new CoverageBasedEnergyScheduler();
                break;
            default:
                throw new IllegalArgumentException("Unsupported energy scheduler type: " + type);
        }
        return scheduler;
    }
}
