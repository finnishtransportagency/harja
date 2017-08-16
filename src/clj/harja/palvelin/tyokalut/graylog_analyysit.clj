(ns harja.palvelin.tyokalut.graylog-analyysit
  (:require [taoensso.timbre :as log]
            [harja.pvm :as pvm]
            [harja.palvelin.tyokalut.graylog-muunnokset :as muunnokset]))

(defn yrita-korjata
  [rikkinainen]
  (map #(let [palvelu (get-in % [:yhteyskatkokset 0 :palvelu])
              katkokset (get-in % [:yhteyskatkokset 0 :katkokset])]
          {:yhteyskatkokset [{:palvelu palvelu :katkokset katkokset}]})
       rikkinainen))

(defn ok-yhteyskatkos-data
  [yhteyskatkokset]
  (filter :yhteyskatkokset yhteyskatkokset))

(defn rikkinainen-yhteyskatkos-data
  [yhteyskatkokset]
  (keep #(when (:rikkinainen %) (:rikkinainen %))  yhteyskatkokset))

(defn ota-mapin-n-suurinta-arvoa
  [mappi n]
  (into {}
        (take-last n (sort-by (fn [avain-arvo-pari]
                                (second avain-arvo-pari))
                              mappi))))

(defn rikkinaisten-yhteyskatkosten-analyysi
  [rikkinainen-data]
  (let [lukumaara (count (filter #(when (or (:kayttaja %) ; jotta sama rikkinainen lasketaan vain kerran
                                            (= % "foo"))
                                      true)
                                 rikkinainen-data))
        eheytetyt-yhteyskatkokset (yrita-korjata (filter #(when-not (= % "foo")
                                                            true)
                                                         rikkinainen-data))
        eheytetyt-yhteyskatkokset-lkm (count eheytetyt-yhteyskatkokset)]
    {:rikkinaiset-lkm lukumaara
     :eheytetyt-lkm eheytetyt-yhteyskatkokset-lkm
     :eheytetyt-yhteyskatkokset eheytetyt-yhteyskatkokset}))

(defn yhteyskatkokset-ryhmittain-analyysi
  [yhteyskatkos-data]
  (let [yhteyskatkokset-ryhmittain (map muunnokset/yhteyskatkokset-ryhmittain yhteyskatkos-data)
        katkokset-ynnatty (apply muunnokset/yhdista-avaimet-kun + :katkokset :palvelu (mapcat :yhteyskatkokset yhteyskatkokset-ryhmittain))]
    {:eniten-katkoksia (take-last 5 (sort-by #(:katkokset %) katkokset-ynnatty))}))

(defn yhteyskatkokset-analyysi
  [yhteyskatkos-data]
  (let [katkokset-ynnatty (apply muunnokset/yhdista-avaimet-kun + :katkokset :palvelu (mapcat :yhteyskatkokset yhteyskatkos-data))]
    {:eniten-katkoksia (take-last 5 (sort-by #(:katkokset %) katkokset-ynnatty))}))

(defn analyysit-yhteyskatkoksista
  [yhteyskatkokset {analysointimetodi :analysointimetodi haettavat-analyysit :haettavat-analyysit}]
  (let [ok-yhteyskatkos-data (ok-yhteyskatkos-data yhteyskatkokset)
        rikkinainen-yhteyskatkos-data (rikkinainen-yhteyskatkos-data yhteyskatkokset)
        rikkinaiset-lokitukset (when (contains? haettavat-analyysit :rikkinaiset-lokitukset)
                                  (rikkinaisten-yhteyskatkosten-analyysi rikkinainen-yhteyskatkos-data))
        yhteyskatkos-data (concat ok-yhteyskatkos-data (:eheytetyt-yhteyskatkokset rikkinaiset-lokitukset))
        yhteyskatkokset-ryhmittain-analyysi (when (contains? haettavat-analyysit :eniten-katkosryhmia)
                                              (yhteyskatkokset-ryhmittain-analyysi yhteyskatkos-data))
        eniten-katkoksia (when (contains? haettavat-analyysit :eniten-katkoksia)
                          (yhteyskatkokset-analyysi yhteyskatkos-data))]
        ; katkoksien-pituudet #(let [ensimmainen-ms (when-let [ek (:ensimmainen-katkos %)]
        ;                                             (.getTime ek))
        ;                            viimeinen-ms (when-let [vk (:viimeinen-katkos %)]
        ;                                          (.getTime vk))]
        ;                       (if (and ensimmainen-ms viimeinen-ms)
        ;                         (Math/abs (- viimeinen-ms ensimmainen-ms)) ;abs, koska lokituksessa oli bugi alussa, jolloin ensimmainen olikin viimeinen
        ;                         0))
        ; pisimmat-katkokset (when (contains? haettavat-analyysit :pisimmat-katkokset)
        ;                       (ota-mapin-n-suurinta-arvoa (reduce #(if (contains? %1 (:palvelu %2))
        ;                                                              (update %1 (:palvelu %2) (fn [palvelun-katkoksen-pituus]
        ;                                                                                          (let [tarkasteltavan-mapin-katkoksen-pituus (katkoksien-pituudet %2)]
        ;                                                                                            (if (> tarkasteltavan-mapin-katkoksen-pituus palvelun-katkoksen-pituus)
        ;                                                                                              tarkasteltavan-mapin-katkoksen-pituus
        ;                                                                                              palvelun-katkoksen-pituus))))
        ;                                                              (assoc %1 (:palvelu %2) (katkoksien-pituudet %2)))
        ;                                                           {} yhteyskatkokset)
        ;                                                   5))
        ; selain-sammutettu-katkoksen-aikana (when (contains? haettavat-analyysit :selain-sammutettu-katkoksen-aikana)
        ;                                      (reduce #(let [lokitus-tapahtui (.getTime (pvm/dateksi (:pvm %2)))
        ;                                                     ping-yhteyskatkokset (some (fn [palvelun-katkokset]
        ;                                                                                  (when (= "ping" (:palvelu palvelun-katkokset))
        ;                                                                                    palvelun-katkokset))
        ;                                                                                (:yhteyskatkokset %2))
        ;                                                     ; Tämä tehdään siltä varalta, että pingiä ei kerettyä tehdä. Siinä tapauksessa otetaan vain joku palvelukutsu
        ;                                                     ping-yhteyskatkokset (if ping-yhteyskatkokset
        ;                                                                             ping-yhteyskatkokset
        ;                                                                             (first (:yhteyskatkokset %2)))
        ;                                                     viimeinen-pingaus (if (> (.getTime (:viimeinene-katkos ping-yhteyskatkokset))
        ;                                                                              (.getTime (:ensimmainen-katkos ping-yhteyskatkokset)))
        ;                                                                          (.getTime (:viimeinen-katkos ping-yhteyskatkokset))
        ;                                                                          (.getTime (:ensimmainen-katkos ping-yhteyskatkokset)))
        ;                                                     lokituksen-ja-pingauksen-vali (- lokitus-tapahtui viimeinen-pingaus)
        ;                                                     kutsutut-palvelut (keep (fn [palvelun-katkokset]
        ;                                                                               (if (= "ping" (:palvelu palvelun-katkokset))
        ;                                                                                 nil
        ;                                                                                 (:palvelu palvelun-katkokset)))
        ;                                                                             (:yhteyskatkokset %2))]
        ;                                                    (if (> lokituksen-ja-pingauksen-vali 10000)
        ;                                                      (merge-with + %1 (zipmap kutsutut-palvelut
        ;                                                                               (repeat (count kutsutut-palvelut) 1)))
        ;                                                      %1))
        ;                                              {} ok-yhteyskatkos-data))]
    {:eniten-katkoksia eniten-katkoksia ;:pisimmat-katkokset pisimmat-katkokset
     :rikkinaiset-lokitukset rikkinaiset-lokitukset :yhteyskatkokset-ryhmittain-analyysi yhteyskatkokset-ryhmittain-analyysi}))
    ;  :selain-sammutettu-katkoksen-aikana selain-sammutettu-katkoksen-aikana
    ;  :eheytetyt-yhteyskatkokset-lkm eheytetyt-yhteyskatkokset-lkm}))
