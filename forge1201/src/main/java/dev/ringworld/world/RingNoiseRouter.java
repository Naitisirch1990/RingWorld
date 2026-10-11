package dev.ringworld.world;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;

/** Applies annular coordinates only at density leaves that consume X/Z. */
public final class RingNoiseRouter {
    private RingNoiseRouter() {}

    public static NoiseRouter wrap(
            NoiseRouter router,
            RingWorldLayouts.RingLayout layout,
            int mappingVersion) {
        return router.mapAll(new AnnularVisitor(
                RingNoiseCoordinates.forLayout(layout, mappingVersion), mappingVersion));
    }

    private record AnnularVisitor(RingNoiseCoordinates coordinates, int mappingVersion)
            implements DensityFunction.Visitor {
        @Override
        public DensityFunction apply(DensityFunction function) {
            if (isCoordinateConsumer(function)) {
                return new AnnularDensityFunction(function, coordinates);
            }
            return function;
        }

        private boolean isCoordinateConsumer(DensityFunction function) {
            return function instanceof RingCoordinateDensityFunction
                    || (mappingVersion >= RingTerrainNoiseMapping.ANNULAR_COMPLETE_V2
                    && function instanceof BlendedNoise);
        }
    }

    private record AnnularDensityFunction(
            DensityFunction delegate,
            RingNoiseCoordinates coordinates) implements DensityFunction {
        @Override
        public double compute(FunctionContext context) {
            return delegate.compute(transform(context));
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            delegate.fillArray(values, new TransformingProvider(provider, coordinates));
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new AnnularDensityFunction(delegate.mapAll(visitor), coordinates));
        }

        @Override
        public double minValue() {
            return delegate.minValue();
        }

        @Override
        public double maxValue() {
            return delegate.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return delegate.codec();
        }

        private FunctionContext transform(FunctionContext source) {
            if (source instanceof AnnularContext) return source;
            int sourceX = source.blockX();
            int sourceZ = source.blockZ();
            return new AnnularContext(
                    coordinates.noiseX(sourceX, sourceZ),
                    source.blockY(),
                    coordinates.noiseZ(sourceX, sourceZ),
                    source.getBlender());
        }
    }

    private record TransformingProvider(
            DensityFunction.ContextProvider delegate,
            RingNoiseCoordinates coordinates) implements DensityFunction.ContextProvider {
        @Override
        public DensityFunction.FunctionContext forIndex(int index) {
            DensityFunction.FunctionContext source = delegate.forIndex(index);
            if (source instanceof AnnularContext) return source;
            int sourceX = source.blockX();
            int sourceZ = source.blockZ();
            return new AnnularContext(
                    coordinates.noiseX(sourceX, sourceZ),
                    source.blockY(),
                    coordinates.noiseZ(sourceX, sourceZ),
                    source.getBlender());
        }

        @Override
        public void fillAllDirectly(double[] densities, DensityFunction function) {
            delegate.fillAllDirectly(densities, new AnnularDensityFunction(function, coordinates));
        }
    }

    private record AnnularContext(int blockX, int blockY, int blockZ, Blender getBlender)
            implements DensityFunction.FunctionContext {
    }
}
