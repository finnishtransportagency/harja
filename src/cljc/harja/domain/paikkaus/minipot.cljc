(ns harja.domain.paikkaus.minipot
  "Ylläpidon paikkausurakoissa käytettävän MINIPOT-lomakkeen skeemat."
  (:require [schema.core :as s]
            [harja.domain.skeema :as skeema]))

(def +paikkaustyot+
  "Kaikki paikkaustyöt MINIPOT-lomake Excelistä"
  [{:nimi "KT-VA" :lyhenne "KT-VA" :koodi 1}
   {:nimi "Keikka" :lyhenne "VA" :koodi 2}
   {:nimi "Reuna- tai painumakorjaus" :lyhenne "REMO/REM" :koodi 3}
   {:nimi "Päällystys" :lyhenne "LTA/MP" :koodi 4}
   {:nimi "SIPA" :lyhenne "SIPA" :koodi 5}
   {:nimi "Puhallus-sip" :lyhenne "" :koodi 6}
   {:nimi "Muu" :lyhenne "" :koodi 7}])

(def +paikkaustyo+ "Paikkaustyön valinta koodilla"
  (apply s/enum (map :koodi +paikkaustyot+)))

(defn hae-paikkaustyo-koodilla
  "Hakee paikkaustyön nimen koodilla"
  [koodi]
  (:nimi (first (filter #(= (:koodi %) koodi) +paikkaustyot+))))

(def +paikkausilmoitus+
  {:osoitteet
   [{:tie                        s/Int
     :aosa                       s/Int
     :aet                        s/Int
     :losa                       s/Int
     :let                        s/Int
     :paallysteen_leveys         s/Num
     :paallysteen_neliot         s/Num
     :paikkausneliot             s/Num
     :paikkausprosentti          s/Num
     (s/optional-key :poistettu) s/Bool}]

   :toteumat
   [{:suorite                    +paikkaustyo+
     :yksikko                    s/Str
     :maara                      s/Num
     :yks_hint_alv_0             s/Num
     :takuupvm                   s/Inst
     (s/optional-key :poistettu) s/Bool}]})