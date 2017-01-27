(ns harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen
  (:require [taoensso.timbre :as log]
            [harja.math :as math]))

(defn- etsi-merkinta-m-metrin-paassa-pisteesta-n
  "Käy annetut merkinnät läpi ja palauttaa merkinnän, joka on
   tarkimmin M metrin päässä pisteestä N."
  [{:keys [etsittavat-merkinnat m n]}]
  (let [merkintojen-etaisyys-pisteeseen-n (map
                                            #(math/pisteiden-etaisyys (:sijainti %) n)
                                            etsittavat-merkinnat)
        n-etaisyyden-ero-m (map
                             #(Math/abs (- % m))
                             merkintojen-etaisyys-pisteeseen-n)
        pienin-ero-m (first (sort n-etaisyyden-ero-m))
        pienin-ero-m-indeksi (.indexOf n-etaisyyden-ero-m pienin-ero-m)]
    (nth etsittavat-merkinnat pienin-ero-m-indeksi)))

(defn- maarita-ymparikaantymisen-indeksi
  "Ottaa kaikki merkinnät nykyisestä käsiteltävästä sijainnista K:n.
   Palauttaa indeksin, jossa ympärikääntyminen tapahtui.
   Palautettu indeksi eli ympärikääntyminen päätellään olevan nykyisen sijainnin
   ja K:n väliin jäävistä pisteistä se, joka on kaikista kauimpana
   K:sta (tätä pistettä aiemmin etäännyttiin K:sta ja eteenpäin aletaan lähentyä K:ta)"
  [kaikki-merkinnat merkinnat-nykyisesta-khon k-sijainti]
  (log/debug "Määritetään ympärikääntymisen indeksi merkinnöistä (" (count merkinnat-nykyisesta-khon) "kpl)")
  (let [etaisyydet-khon (map #(math/pisteiden-etaisyys (:sijainti %)
                                                       k-sijainti)
                             merkinnat-nykyisesta-khon)
        kaukaisin (last (sort etaisyydet-khon))
        kaukaisin-indeksi (.indexOf etaisyydet-khon kaukaisin)
        kaukaisin-merkinta (nth merkinnat-nykyisesta-khon kaukaisin-indeksi)
        ymparikaantymisen-indeksi (.indexOf kaikki-merkinnat kaukaisin-merkinta)]


    (log/debug (str "Etäisyydet K:n" (pr-str etaisyydet-khon)))
    (log/debug (str "Kaukaisin etäisyys K:n " kaukaisin))
    (log/debug (str "Kaukaisin etäisyys K:n indeksi" kaukaisin-indeksi))
    (log/debug (str "Kaukaisin merkintä" kaukaisin-merkinta))
    (log/debug (str "Määritetty ympärikääntyminen indeksiin: " ymparikaantymisen-indeksi))
    ymparikaantymisen-indeksi))

(defn- k-nykyisessa-sijainnissa? [k-sijainti nykyinen-sijainti]
  (<= (math/pisteiden-etaisyys k-sijainti nykyinen-sijainti) 30))

(defn- maarita-seuraava-k [{:keys [tulos
                                   kaikki-merkinnat
                                   kasiteltava-indeksi
                                   seuraava-merkinta
                                   k-indeksi m]}]
  (let [sopiva-merkinta-takana (etsi-merkinta-m-metrin-paassa-pisteesta-n
                                 {:etsittavat-merkinnat (drop
                                                          k-indeksi
                                                          (:lapikaydyt-merkinnat tulos))
                                  :m m
                                  :n (:sijainti seuraava-merkinta)})
        sopiva-merkinta-takana-indeksi (.indexOf (:lapikaydyt-merkinnat tulos) sopiva-merkinta-takana)]
    ;; Siirrä K:ta eteenpäin niin kauan että saavutetaan takaa löydetty sopiva merkintä
    ;; Jokaisella siirrolla tulee tarkistaa, siirtyikö K suunnilleen samaan pisteeseen
    ;; kuin missä nyt ollaan. Jos siirtyi, tapahtui ympärikääntyminen takana löydetystä
    ;; merkinnästä
    (loop [uusi-k-indeksi k-indeksi
           ymparikaantyminen-indeksissa nil]
      (let [uusi-k-sijainti (:sijainti (nth (:lapikaydyt-merkinnat tulos) uusi-k-indeksi))]
        (if (= uusi-k-indeksi sopiva-merkinta-takana-indeksi)
          (do (log/debug "K on sopivassa sijainnissa M metrin päässä.")
              {:sijainti (:sijainti sopiva-merkinta-takana)
               :indeksi uusi-k-indeksi
               :ymparikaantyminen-indeksi ymparikaantyminen-indeksissa})
          (do (log/debug (str "Siirretään K:ta eteenpäin indeksiin: " (inc uusi-k-indeksi)))
              (recur (inc uusi-k-indeksi)
                     ;; Kokeile törmääkö K nykyiseen sijaintiin
                     ;; eli havaitaanko ympärikääntyminen, ellei jo havaittu
                     (if ymparikaantyminen-indeksissa
                       ymparikaantyminen-indeksissa
                       (when (k-nykyisessa-sijainnissa? uusi-k-sijainti (:sijainti seuraava-merkinta))
                         (log/debug "--> K kohtasi nykyisen sijainnin!")
                         (log/debug "--> Käsiteltava indeksi: " kasiteltava-indeksi " ja uusi k indeksi: " uusi-k-indeksi)
                         (log/debug "--> Ympärikääntymisen piste täytyy löytä näiden välistä!")
                         (maarita-ymparikaantymisen-indeksi
                           kaikki-merkinnat
                           (take (- kasiteltava-indeksi uusi-k-indeksi)
                                 (drop uusi-k-indeksi kaikki-merkinnat))
                           uusi-k-sijainti))))))))))

(defn- yrita-maarittaa-ensimmainen-k [ensimmainen-merkinta seuraava-merkinta m]
  (when (>= (math/pisteiden-etaisyys (:sijainti ensimmainen-merkinta)
                                     (:sijainti seuraava-merkinta))
            m)
    (log/debug "Määritetään K ensimmäiseen pisteeseen")
    {:sijainti (:sijainti ensimmainen-merkinta)
     :indeksi 0}))

(defn- maarita-uusi-k
  "Mikäli K:ta ei ole määritelty, määrittää K:n ensimmäiseen sijaintiin
   kun ollaan vähintään M metrin päässä.

   Mikäli K on määritelty, määrittelee K:lle uuden sijainnin niin, että se on aina
   suunnilleen M metrin päässä nykyisestä sijainnista (seuraava-merkinta), jollain
   jo ajetun reitin pisteistä.

   K:n sijainti määritellään uudelleen siirtämällä sitä jo ajettua reittiä pitkin eteenpäin
   niin kauan, että se sijaitsee uudessa pisteessä M metrin päässä nykyisestä sijainnista.

   Mikäli K:n siirron aikana havaitaan K:n olevan suunnilleen samassa pisteessä kuin nykyinen
   sijainti, ollaan havaittu ympärikääntyminen. Tällöin etsitään se sijainti, jossa
   ympärikääntyminen tapahtui (sijainti, jossa alettiin lähestyä K:ta)."
  [{:keys [ensimmainen-merkinta tulos
           seuraava-merkinta kasiteltava-indeksi
           k-indeksi m kaikki-merkinnat]}]
  (log/debug "Käsitellään merkintä indeksissä: " kasiteltava-indeksi)
  (if (nil? (:k-indeksi tulos))
    ;; K:ta ei ole vielä määritelty, määrittele se ensimmäiseen pisteeseen
    ;; kunhan nykyinen käsiteltävä piste on riittävän kaukana
    (yrita-maarittaa-ensimmainen-k ensimmainen-merkinta seuraava-merkinta m)
    ;; Käy edelliset pisteet läpi K:sta eteenpäin ja etsi uusi sellainen piste, joka
    ;; on M metrin päässä nykyisestä pisteestä.
    (maarita-seuraava-k {:tulos tulos
                         :kasiteltava-indeksi kasiteltava-indeksi
                         :kaikki-merkinnat kaikki-merkinnat
                         :seuraava-merkinta seuraava-merkinta
                         :k-indeksi k-indeksi
                         :m m})))

(defn- analysoi-ymparikaantymiset
  "Käy reittimerkinnät läpi ja palauttaa vectorin indeksejä, joissa ympärikääntyminen havaittiin.

  Parametrit:
  merkinnat       Analysoitavat merkinnät
  m               Kuinka kauas taaksepäin tulee ajaa, jotta tulkitaan ympärikääntymiseksi.
                  Arvo ei voi olla kovin pieni, sillä GPS on epätarkka ja erityisesti paikkalla
                  ollessa pisteet saattavat sijoittua lähelle toisiaan."
  [merkinnat m]
  (let [tulos (reduce
                (fn [tulos seuraava]
                  (let [uusi-k (maarita-uusi-k {:ensimmainen-merkinta (first merkinnat)
                                                :kaikki-merkinnat merkinnat
                                                :tulos tulos
                                                :kasiteltava-indeksi (:kasiteltava-indeksi tulos)
                                                :seuraava-merkinta seuraava
                                                :k-indeksi (:k-indeksi tulos)
                                                :m m})]
                    {:kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos))
                     :k-indeksi (:indeksi uusi-k)
                     :k-sijainti (:sijainti uusi-k)
                     :lapikaydyt-merkinnat (conj (:lapikaydyt-merkinnat tulos) seuraava)
                     :ymparikaantymisindeksit (if (:ymparikaantyminen-indeksi uusi-k)
                                                (conj (:ymparikaantymisindeksit tulos)
                                                      (:ymparikaantyminen-indeksi uusi-k))
                                                (:ymparikaantymisindeksit tulos))}))
                {:kasiteltava-indeksi 0
                 ;; K on piste, joka on aina jossain edellisistä sijainneista, noin m metrin päässä.
                 :k-indeksi nil
                 :k-sijainti nil
                 :lapikaydyt-merkinnat []
                 :ymparikaantymisindeksit []}
                merkinnat)]
    (:ymparikaantymisindeksit tulos)))

(defn lisaa-tieto-ymparikaantymisesta
  [merkinnat]
  (let [ymparikaantymiset (set (analysoi-ymparikaantymiset merkinnat 100))
        merkinnat (vec (map-indexed (fn [index merkinta]
                                      (if (ymparikaantymiset index)
                                        (assoc merkinta :ymparikaantyminen? true)
                                        merkinta))
                                    merkinnat))]
    merkinnat))