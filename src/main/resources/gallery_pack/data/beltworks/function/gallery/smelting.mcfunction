# Smelting columns: three furnaces, each between a hopper above and one below. A feeder puts coal from a chest
# straight into the furnace's side, the only face a furnace takes fuel by; one above puts raw ore into the top
# hopper, and one at the foot takes the ingots out of the bottom hopper onto a belt the columns share, which
# runs east to a chest. A feeder's arms reach sideways, so the furnace's top and bottom faces go through hoppers.
function beltworks:gallery/clear
fill ~8 ~ ~-8 ~8 ~ ~-7 minecraft:stone_bricks
setblock ~8 ~1 ~-8 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:coal",count:32}]}
setblock ~8 ~1 ~-7 beltworks:feeder[facing=south]
setblock ~8 ~2 ~-8 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:raw_iron",count:64}]}
setblock ~8 ~2 ~-7 beltworks:feeder[facing=south]
setblock ~8 ~2 ~-6 minecraft:hopper[facing=down]
setblock ~8 ~1 ~-6 minecraft:furnace[facing=south]
setblock ~8 ~ ~-6 minecraft:hopper[facing=down]
setblock ~8 ~ ~-5 beltworks:feeder[facing=south]
fill ~13 ~ ~-8 ~13 ~ ~-7 minecraft:stone_bricks
setblock ~13 ~1 ~-8 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:coal",count:32}]}
setblock ~13 ~1 ~-7 beltworks:feeder[facing=south]
setblock ~13 ~2 ~-8 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:raw_copper",count:64}]}
setblock ~13 ~2 ~-7 beltworks:feeder[facing=south]
setblock ~13 ~2 ~-6 minecraft:hopper[facing=down]
setblock ~13 ~1 ~-6 minecraft:furnace[facing=south]
setblock ~13 ~ ~-6 minecraft:hopper[facing=down]
setblock ~13 ~ ~-5 beltworks:feeder[facing=south]
fill ~18 ~ ~-8 ~18 ~ ~-7 minecraft:stone_bricks
setblock ~18 ~1 ~-8 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:coal",count:32}]}
setblock ~18 ~1 ~-7 beltworks:feeder[facing=south]
setblock ~18 ~2 ~-8 minecraft:chest[facing=south]{Items:[{Slot:0b,id:"minecraft:raw_gold",count:64}]}
setblock ~18 ~2 ~-7 beltworks:feeder[facing=south]
setblock ~18 ~2 ~-6 minecraft:hopper[facing=down]
setblock ~18 ~1 ~-6 minecraft:furnace[facing=south]
setblock ~18 ~ ~-6 minecraft:hopper[facing=down]
setblock ~18 ~ ~-5 beltworks:feeder[facing=south]
fill ~7 ~ ~-4 ~20 ~ ~-4 beltworks:belt_tile[facing=east]
setblock ~21 ~ ~-4 beltworks:loader[facing=west]
setblock ~22 ~ ~-4 minecraft:chest[facing=north]
function beltworks:gallery/sign {scene:"smelting"}
