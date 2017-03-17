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

(defn tarkastuksen-havainto-ok? [data]
  (boolean (#{"ok" "OK" "Ok" "oK"} (:havainnot data))))

(defn tarkastus-sisaltaa-havaintoja? [data]
  (boolean
    (or (not-empty (:vakiohavainnot data))
        (and (not-empty (:havainnot data)) (not (tarkastuksen-havainto-ok? data)))
        (not-empty (:talvihoitomittaus data))
        (not-empty (:soratiemittaus data)))))

(defn liukas-vakiohavainto? [data]
  (boolean
    (and
      (not-empty (:vakiohavainnot data))
      (str/includes? (:vakiohavainnot data) "Liukasta"))))

(defn luminen-vakiohavainto? [data]
  (boolean
    (and
      (not-empty (:vakiohavainnot data))
      (str/includes? (:vakiohavainnot data) "Lumista"))))

(defn luminen-tai-liukas-vakiohavainto? [data]
  (boolean
    (or (liukas-vakiohavainto? data) (luminen-vakiohavainto? data))))

(def talvihoitomittauksen-lomakekentat
  [[:lumimaara] [:tasaisuus] [:kitka] [:lampotila :tie] [:lampotila :ilma]])

(def talvihoitomittauksen-kentat
  [[:tarkastus] [:lumimaara] [:hoitoluokka] [:tasaisuus] [:kitka] [:ajosuunta]
   [:lampotila :tie] [:lampotila :ilma]])

(def soratiemittauksen-kentat
  [[:tarkastus] [:tasaisuus] [:polyavyys] [:kiinteys] [:sivukaltevuus] [:hoitoluokka]])

(defn tarkastus-tiedolla-onko-ok
  "Tämä kertoo onko laadunalitus"
  [{laadunalitus :laadunalitus :as tarkastus}]
  (assoc tarkastus :ok? (not laadunalitus)))
