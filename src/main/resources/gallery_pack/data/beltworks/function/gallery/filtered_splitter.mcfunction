# A filtered splitter: iron ingots go out its left belt, to the far chest, and everything else out its right.
function beltworks:gallery/clear
setblock ~5 ~ ~-7 minecraft:chest[facing=north]{Items:[{Slot:0b,id:"minecraft:iron_ingot",count:64},{Slot:1b,id:"minecraft:copper_ingot",count:64},{Slot:2b,id:"minecraft:gold_ingot",count:64},{Slot:3b,id:"minecraft:redstone",count:64},{Slot:4b,id:"minecraft:coal",count:64},{Slot:5b,id:"minecraft:lapis_lazuli",count:64},{Slot:6b,id:"minecraft:iron_ingot",count:64},{Slot:7b,id:"minecraft:copper_ingot",count:64},{Slot:8b,id:"minecraft:gold_ingot",count:64},{Slot:9b,id:"minecraft:redstone",count:64},{Slot:10b,id:"minecraft:coal",count:64},{Slot:11b,id:"minecraft:lapis_lazuli",count:64},{Slot:12b,id:"minecraft:iron_ingot",count:64},{Slot:13b,id:"minecraft:copper_ingot",count:64},{Slot:14b,id:"minecraft:gold_ingot",count:64},{Slot:15b,id:"minecraft:redstone",count:64},{Slot:16b,id:"minecraft:coal",count:64},{Slot:17b,id:"minecraft:lapis_lazuli",count:64}]}
setblock ~6 ~ ~-7 beltworks:loader[facing=east]
fill ~7 ~ ~-7 ~11 ~ ~-7 beltworks:belt_tile[facing=east]
setblock ~12 ~ ~-7 beltworks:splitter[facing=east,side=left]{output_priority:"LEFT",splitter_filter:{id:"minecraft:iron_ingot",count:1}}
setblock ~12 ~ ~-6 beltworks:splitter[facing=east,side=right]
fill ~13 ~ ~-7 ~20 ~ ~-7 beltworks:belt_tile[facing=east]
fill ~13 ~ ~-6 ~20 ~ ~-6 beltworks:belt_tile[facing=east]
setblock ~21 ~ ~-7 beltworks:loader[facing=west]
setblock ~22 ~ ~-7 minecraft:chest[facing=north]
setblock ~21 ~ ~-6 beltworks:loader[facing=west]
setblock ~22 ~ ~-6 minecraft:chest[facing=north]
function beltworks:gallery/sign {scene:"filtered_splitter"}
