(ns harja.domain.laadunseuranta.tarkastukset
  (:require [clojure.string :as str]
            [harja.domain.hoitoluokat :as hoitoluokat]))

(def +tarkastustyyppi->nimi+
  {:tiesto "Tiestötarkastus"
    :talvihoito "Kelitarkastus"
    :soratie "Soratien tarkastus"
    :laatu "Laaduntarkastus"
    :pistokoe "Pistokoe"
    :katselmus "Katselmus"
    :vastaanotto "Vastaanottotarkastus"
    :takuu "Takuutarkastus"})

(defn formatoi-talvihoitomittaukset
  [thm]
  (let [{kitka :kitka lumimaara :lumimaara tasaisuus :tasaisuus
         {tie :tie ilma :ilma} :lampotila} thm]
    (when (or kitka lumimaara tasaisuus)
      (str "Talvihoitomittaukset: "
           (str/replace
             (str/join ", "
                       (keep #(if (and (some? (val %))
                                       (not= "" (val %))) ;; tyhjä hoitoluokka pois
                                (if (= :lampotila (key %))
                                  (when (or tie ilma)
                                    (str (when tie (str "tie: " tie "°C"))
                                         (when (and tie ilma) ", ")
                                         (when ilma (str "ilma: " ilma "°C"))))
                                  (str (name (key %)) ": "
                                       (if (= :hoitoluokka (key %))
                                         (hoitoluokat/talvihoitoluokan-nimi-str (val %))
                                         (if (or
                                               (= :lumimaara (key %))
                                               (= :tasaisuus (key %)))
                                           (str (val %) "cm")
                                           (val %)))))
                                nil)
                             (select-keys
                               thm
                               [:hoitoluokka :kitka :lumimaara :tasaisuus :lampotila])))
             "lumimaara" "lumimäärä")))))

(defn formatoi-soratiemittaukset
  [stm]
  (let [{tasaisuus :tasaisuus kiinteys :kiinteys polyavyys :polyavyys
         sivukaltevuus :sivukaltevuus hoitoluokka :hoitoluokka} stm]
    (when (or tasaisuus kiinteys polyavyys sivukaltevuus hoitoluokka)
      (str "Soratiemittaukset: "
           (str/replace
             (str/join ", "
                       (keep #(if (and (some? (val %))
                                       (not= "" (val %))) ;; tyhjä hoitoluokka pois
                                (str (name (key %)) ": " (val %))
                                nil)
                             (select-keys
                               stm
                               [:hoitoluokka :tasaisuus :kiinteys :polyavyys :sivukaltevuus])))
             "polyavyys" "pölyävyys")))))

(defn formatoi-vakiohavainnot [vakiohavainnot]
  (str/join ", " (keep identity vakiohavainnot)))