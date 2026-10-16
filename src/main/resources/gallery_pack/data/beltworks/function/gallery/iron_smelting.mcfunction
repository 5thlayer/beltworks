# Iron smelting from two belts, into two furnaces side by side. A line of raw iron two blocks up runs behind them,
# where a feeder for each puts ore into the hopper on its top, the face a furnace takes ore by. A line of coal
# climbs from the floor to a block up beside it, where a feeder at each end puts coal into a hopper against the
# furnace's outer side, the face it takes fuel by. Both lines end where they stop, so their items back up and
# wait. Under each furnace a hopper takes the ingots, and a feeder puts them onto a belt that runs into a chest.
function beltworks:gallery/clear
setblock ~6 ~2 ~-8 minecraft:chest[facing=east]{Items:[{Slot:0b,id:"minecraft:raw_iron",count:64},{Slot:1b,id:"minecraft:raw_iron",count:64},{Slot:2b,id:"minecraft:raw_iron",count:64},{Slot:3b,id:"minecraft:raw_iron",count:64},{Slot:4b,id:"minecraft:raw_iron",count:64},{Slot:5b,id:"minecraft:raw_iron",count:64},{Slot:6b,id:"minecraft:raw_iron",count:64},{Slot:7b,id:"minecraft:raw_iron",count:64},{Slot:8b,id:"minecraft:raw_iron",count:64}]}
setblock ~7 ~2 ~-8 beltworks:loader[facing=east]
fill ~8 ~2 ~-8 ~15 ~2 ~-8 beltworks:belt_tile[facing=east]
setblock ~6 ~ ~-8 minecraft:chest[facing=east]{Items:[{Slot:0b,id:"minecraft:coal",count:64},{Slot:1b,id:"minecraft:coal",count:64},{Slot:2b,id:"minecraft:coal",count:64},{Slot:3b,id:"minecraft:coal",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:coal",count:64},{Slot:6b,id:"minecraft:coal",count:64},{Slot:7b,id:"minecraft:coal",count:64},{Slot:8b,id:"minecraft:coal",count:64}]}
setblock ~7 ~ ~-8 beltworks:loader[facing=east]
setblock ~7 ~1 ~-8 minecraft:stone_bricks
setblock ~8 ~ ~-8 beltworks:belt_tile[facing=east]
setblock ~9 ~ ~-8 beltworks:belt_tile[facing=east,climb=up]
fill ~10 ~1 ~-8 ~15 ~1 ~-8 beltworks:belt_tile[facing=east]
fill ~12 ~ ~-7 ~15 ~ ~-7 minecraft:stone_bricks
setblock ~12 ~ ~-6 minecraft:stone_bricks
setblock ~15 ~ ~-6 minecraft:stone_bricks
setblock ~13 ~2 ~-7 beltworks:feeder[facing=south]
setblock ~14 ~2 ~-7 beltworks:feeder[facing=south]
setblock ~13 ~2 ~-6 minecraft:hopper[facing=down]
setblock ~14 ~2 ~-6 minecraft:hopper[facing=down]
setblock ~12 ~1 ~-7 beltworks:feeder[facing=south]
setblock ~15 ~1 ~-7 beltworks:feeder[facing=south]
setblock ~12 ~1 ~-6 minecraft:hopper[facing=east]
setblock ~15 ~1 ~-6 minecraft:hopper[facing=west]
setblock ~13 ~1 ~-6 minecraft:furnace[facing=south]
setblock ~14 ~1 ~-6 minecraft:furnace[facing=south]
setblock ~13 ~ ~-6 minecraft:hopper[facing=down]
setblock ~14 ~ ~-6 minecraft:hopper[facing=down]
setblock ~13 ~ ~-5 beltworks:feeder[facing=south]
setblock ~14 ~ ~-5 beltworks:feeder[facing=south]
fill ~12 ~ ~-4 ~15 ~ ~-4 beltworks:belt_tile[facing=east]
setblock ~16 ~ ~-4 beltworks:loader[facing=west]
setblock ~17 ~ ~-4 minecraft:chest[facing=west]
function beltworks:gallery/sign {scene:"iron_smelting"}
