(ns harja.domain.paikkausilmoitus
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
     :paallysteen-leveys         s/Num
     :paikkausneliot             s/Num
     (s/optional-key :poistettu) s/Bool}]

   :toteumat
   [{:suorite                         +paikkaustyo+
     (s/optional-key :yksikko)        s/Str
     (s/optional-key :maara)          (s/maybe s/Num)
     (s/optional-key :yks-hint-alv-0) (s/maybe s/Num)
     (s/optional-key :takuupvm)       (s/maybe s/Inst)
     (s/optional-key :poistettu)      s/Bool}]})


(defn laske-kokonaishinta [tyot]
  (reduce +
          (map
            (fn [tyo]
              (if (and (:yks-hint-alv-0 tyo) (:maara tyo))
                (* (:yks-hint-alv-0 tyo) (:maara tyo))
                0))
            tyot)))
