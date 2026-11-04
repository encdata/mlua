package dev.mlua.mcapi.world;

public interface MCInteractionResultHolder<T> {
    MCInteractionResult getResult();
    T getObject();

    static <T> MCInteractionResultHolder<T> pass(T object) {
        return new MCInteractionResultHolder<T>() {
            @Override
            public MCInteractionResult getResult() {
                return MCInteractionResult.PASS;
            }

            @Override
            public T getObject() {
                return object;
            }
        };
    }

    static <T> MCInteractionResultHolder<T> success(T object) {
        return new MCInteractionResultHolder<T>() {
            @Override
            public MCInteractionResult getResult() {
                return MCInteractionResult.SUCCESS;
            }

            @Override
            public T getObject() {
                return object;
            }
        };
    }

    static <T> MCInteractionResultHolder<T> consume(T object) {
        return new MCInteractionResultHolder<T>() {
            @Override
            public MCInteractionResult getResult() {
                return MCInteractionResult.CONSUME;
            }

            @Override
            public T getObject() {
                return object;
            }
        };
    }

    static <T> MCInteractionResultHolder<T> fail(T object) {
        return new MCInteractionResultHolder<T>() {
            @Override
            public MCInteractionResult getResult() {
                return MCInteractionResult.FAIL;
            }

            @Override
            public T getObject() {
                return object;
            }
        };
    }
}
