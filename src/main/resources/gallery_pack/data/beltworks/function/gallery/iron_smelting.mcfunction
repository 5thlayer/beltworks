# Iron smelting from two belts: a line of coal a block up and a line of raw iron two up behind it. One feeder
# picks coal off its line straight into the furnace's side, the face a furnace takes fuel by; the other picks raw
# iron off its line into the hopper on the furnace's top, the face it takes ore by, as no feeder reaches a top.
# A third takes the ingots out of the hopper under it into a chest.
function beltworks:gallery/clear
fill ~5 ~ ~-8 ~22 ~1 ~-8 minecraft:stone_bricks
setblock ~5 ~2 ~-8 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:raw_iron",count:64},{Slot:1b,id:"minecraft:raw_iron",count:64},{Slot:2b,id:"minecraft:raw_iron",count:64},{Slot:3b,id:"minecraft:raw_iron",count:64},{Slot:4b,id:"minecraft:raw_iron",count:64},{Slot:5b,id:"minecraft:raw_iron",count:64},{Slot:6b,id:"minecraft:raw_iron",count:64},{Slot:7b,id:"minecraft:raw_iron",count:64},{Slot:8b,id:"minecraft:raw_iron",count:64}]}
setblock ~6 ~2 ~-8 beltworks:loader[facing=east]
fill ~7 ~2 ~-8 ~20 ~2 ~-8 beltworks:belt_tile[facing=east]
setblock ~21 ~2 ~-8 beltworks:loader[facing=west]
setblock ~22 ~2 ~-8 minecraft:chest[facing=north]
fill ~5 ~ ~-4 ~22 ~0 ~-4 minecraft:stone_bricks
setblock ~5 ~1 ~-4 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:coal",count:64},{Slot:1b,id:"minecraft:coal",count:64},{Slot:2b,id:"minecraft:coal",count:64},{Slot:3b,id:"minecraft:coal",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:coal",count:64},{Slot:6b,id:"minecraft:coal",count:64},{Slot:7b,id:"minecraft:coal",count:64},{Slot:8b,id:"minecraft:coal",count:64}]}
setblock ~6 ~1 ~-4 beltworks:loader[facing=east]
fill ~7 ~1 ~-4 ~20 ~1 ~-4 beltworks:belt_tile[facing=east]
setblock ~21 ~1 ~-4 beltworks:loader[facing=west]
setblock ~22 ~1 ~-4 minecraft:chest[facing=north]
fill ~13 ~ ~-7 ~13 ~1 ~-7 minecraft:stone_bricks
setblock ~13 ~2 ~-7 beltworks:feeder[facing=south]
setblock ~13 ~2 ~-6 minecraft:hopper[facing=down]
setblock ~13 ~1 ~-6 minecraft:furnace[facing=west]
setblock ~13 ~ ~-6 minecraft:hopper[facing=down]
setblock ~13 ~ ~-5 minecraft:stone_bricks
setblock ~13 ~1 ~-5 beltworks:feeder[facing=north]
setblock ~14 ~ ~-6 beltworks:feeder[facing=east]
setblock ~15 ~ ~-6 minecraft:chest[facing=west]
function beltworks:gallery/sign {scene:"iron_smelting"}
