(ns kasane.gltf
  "Thin adapter over org-khronos-glb — this ns used to have its own minimal
   GLB chunk reader; org-khronos-glb already has a complete implementation
   (chunk framing via `glb/parse-glb` + generic JSON via `glb.json/parse`,
   independently developed further than kasane.gltf ever was — it also has
   full accessor/mesh decoding this repo doesn't need). See ADR-2606272100
   and the kotoba-lang reverse-domain media/graphics standards-substrate
   split (com-junkawasaki/root)."
  (:require [glb]
            [glb.json :as glb-json]
            [kasane.bytes :as b]))

(defn parse
  "Parse glTF `data` (auto-detects .glb binary vs .gltf JSON text) →
   {:json <parsed-map> :bin (nilable bytes) :binary? bool}."
  [data]
  (let [bv (vec data)]
    (if (and (>= (count bv) 4) (= (glb/le-bytes->u32 (subvec bv 0 4)) glb/glb-magic))
      (assoc (glb/parse-glb bv) :binary? true)
      {:json (glb-json/parse (b/bytes->ascii bv)) :bin nil :binary? false})))
