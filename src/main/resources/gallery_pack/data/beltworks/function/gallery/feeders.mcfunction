# Feeders, one belt for a row of machines: a line runs east past three feeders, each taking from the tile
# behind it and putting into the barrel in front of it, and on to a chest at its end.
function beltworks:gallery/clear
setblock ~5 ~ ~-8 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:gold_ingot",count:64},{Slot:3b,id:"minecraft:redstone",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:lapis_lazuli",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:gold_ingot",count:64},{Slot:9b,id:"minecraft:redstone",count:64},{Slot:10b,id:"minecraft:coal",count:64},{Slot:11b,id:"minecraft:lapis_lazuli",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:gold_ingot",count:64},{Slot:15b,id:"minecraft:redstone",count:64},{Slot:16b,id:"minecraft:coal",count:64},{Slot:17b,id:"minecraft:lapis_lazuli",count:64}]}
setblock ~6 ~ ~-8 beltworks:loader[facing=east]
fill ~7 ~ ~-8 ~20 ~ ~-8 beltworks:belt_tile[facing=east]
setblock ~21 ~ ~-8 beltworks:loader[facing=west]
setblock ~22 ~ ~-8 minecraft:chest[facing=north]
setblock ~9 ~ ~-7 beltworks:feeder[facing=south]
setblock ~9 ~ ~-6 minecraft:barrel[facing=up]
setblock ~13 ~ ~-7 beltworks:feeder[facing=south]
setblock ~13 ~ ~-6 minecraft:barrel[facing=up]
setblock ~17 ~ ~-7 beltworks:feeder[facing=south]
setblock ~17 ~ ~-6 minecraft:barrel[facing=up]
function beltworks:gallery/sign {scene:"feeders"}
