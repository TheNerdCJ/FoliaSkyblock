-- Chunk-scoped lookups for lazy-loaded chest shops (world + chunk coordinates)
CREATE INDEX IF NOT EXISTS idx_chest_shops_world_chunk
    ON chest_shops(world, (x / 16), (z / 16));