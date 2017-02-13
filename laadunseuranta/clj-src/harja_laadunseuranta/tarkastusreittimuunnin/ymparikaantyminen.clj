(ns harja-laadunseuranta.tarkastusreittimuunnin.ymparikaantyminen
  "Ympärikääntyminen havaitseminen on ongelmallista, koska GPS on luonteeltaan epätarkka.
  Yksi tai edes useampi eri suuntaan osoittava piste ei riitä indikoimaan, että
  ympärikääntyminen on tapahtunut. Lisäksi jos ollaan pitkään paikallaan,
  niin saatu suunta todennäköisesti muuttuu jatkuvasti, mitä ei pidä tulkita ympärikääntymiseksi.

  Käydään reittipisteitä läpi niin kauan, että etäisyys ensimmäiseen pisteeseen on vähintään M metriä.
  Ensimmäinen piste merkitään pisteeksi K. Jatketaan pisteiden läpikäyntiä.
  Jos etäisyys nykyisestä käsiteltävästä pisteestä pisteeseen K on enemmän kuin M metriä,
  siirretään K:ta eteenpäin reittiä pitkin niin kauan, että sen etäisyys nykyiseen pisteeseen
  on lähellä M metriä. Jos K tämän siirron aikana törmää nykyiseen sijaintiin, on selvää, että
  hieman aiemmin on käännytty ympäri.

  Näin jos tietä ajetaan koko ajan yhteen suuntaan, piste K pysyy aina noin M metrin päässä nykyisestä
  käsiteltävästä pisteestä. Jos pysähdytään paikalleen pitkäksi aikaa, niin K pysyy aina
  suurinpiirtein yhtä kaukana. Nyt jos lopulta käännytään ympäri, niin aletaankin lähestyä pistettä K.
  Jos lopulta törmätään pisteeseen K, niin voidaan todeta, että aiemmin tapahtui ympärikääntyminen.
  Valitaan se piste, josta lähentyminen alkoi ja merkitään siihen: :ymparikaantyminen true?"
  (:require [taoensso.timbre :as log]
            [harja.math :as math]))

(defn- etsi-merkinta-m-metrin-paassa-pisteesta-n
  "Käy annetut merkinnät läpi ja palauttaa merkinnän, joka on
   tarkimmin M metrin päässä pisteestä N."
  [{:keys [etsittavat-merkinnat m n]}]
  (let [merkintojen-etaisyys-pisteeseen-n (map
                                            #(let [merkinta-sijainti (:sijainti %)]
                                               (when (and merkinta-sijainti n)
                                                 (math/pisteiden-etaisyys merkinta-sijainti n)))
                                            etsittavat-merkinnat)
        n-etaisyyden-ero-m (map
                             #(when % (Math/abs (- % m)))
                             merkintojen-etaisyys-pisteeseen-n)
        pienin-ero-m (first (sort (remove nil? n-etaisyyden-ero-m)))
        pienin-ero-m-indeksi (when pienin-ero-m
                               (.indexOf n-etaisyyden-ero-m pienin-ero-m))]
    (when pienin-ero-m-indeksi
      (nth etsittavat-merkinnat pienin-ero-m-indeksi))))

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
    (log/debug (str "Määritetty ympärikääntyminen indeksiin: " ymparikaantymisen-indeksi))
    ymparikaantymisen-indeksi))

(defn- k-nykyisessa-sijainnissa? [k-sijainti nykyinen-sijainti]
  (if (and k-sijainti nykyinen-sijainti)
    ;; Pisteet ovat hyvin lähellä toisiaan. Liian tarkkaa arvoa ei voi käyttää, koska
    ;; korkeassa nopeudessa GPS-pisteiden tiheys vähenee.
    ;; Arvon 35 pitäisi kattaa pisteiden törmäys 100km/h vauhdissa, jos pisteitä on saatu otettua
    ;; noin kahden sekunnin välein.
    (<= (math/pisteiden-etaisyys k-sijainti nykyinen-sijainti) 35)
    false)) ;; Ei voida määrittää

(defn- etsi-kn-tormays-nykyiseen-sijaintiin
  "Jos K on nykyisessä sijainnissa, palauttaa indeksin, jossa ympärikääntyminen tapahtui.
   Muuten palauttaa nil."
  [{:keys [kaikki-merkinnat kasiteltava-indeksi
           seuraava-merkinta uusi-k-indeksi
           uusi-k-sijainti]}]
  (when (k-nykyisessa-sijainnissa? uusi-k-sijainti (:sijainti seuraava-merkinta))
    (log/debug "--> K kohtasi nykyisen sijainnin!")
    (log/debug "--> Käsiteltava indeksi: " kasiteltava-indeksi " ja uusi k indeksi: " uusi-k-indeksi)
    (log/debug "--> Ympärikääntymisen piste täytyy löytä näiden välistä!")
    (maarita-ymparikaantymisen-indeksi
      kaikki-merkinnat
      (take (- kasiteltava-indeksi uusi-k-indeksi)
            (drop uusi-k-indeksi kaikki-merkinnat))
      uusi-k-sijainti)))

(defn- maarita-seuraava-k [{:keys [tulos kaikki-merkinnat
                                   kasiteltava-indeksi seuraava-merkinta
                                   k-indeksi m]}]
  (let [sopiva-merkinta-takana (etsi-merkinta-m-metrin-paassa-pisteesta-n
                                 {:etsittavat-merkinnat (drop
                                                          k-indeksi
                                                          (:lapikaydyt-merkinnat tulos))
                                  :m m
                                  :n (:sijainti seuraava-merkinta)})
        sopiva-merkinta-takana-indeksi (when sopiva-merkinta-takana
                                         (.indexOf (:lapikaydyt-merkinnat tulos) sopiva-merkinta-takana))]
    ;; Siirrä K:ta eteenpäin niin kauan että saavutetaan takaa löydetty sopiva merkintä
    ;; Jokaisella siirrolla tulee tarkistaa, siirtyikö K suunnilleen samaan pisteeseen
    ;; kuin missä nyt ollaan. Jos siirtyi, tapahtui ympärikääntyminen takana löydetystä
    ;; merkinnästä
    (when sopiva-merkinta-takana-indeksi
      (loop [uusi-k-indeksi k-indeksi
             ymparikaantyminen-indeksissa nil]
        (let [uusi-k-sijainti (:sijainti (nth (:lapikaydyt-merkinnat tulos) uusi-k-indeksi))]
          (if (= uusi-k-indeksi sopiva-merkinta-takana-indeksi)
            (do (log/debug "K on sopivassa sijainnissa M metrin päässä.")
                {:sijainti (:sijainti sopiva-merkinta-takana)
                 :indeksi uusi-k-indeksi
                 :ymparikaantyminen-indeksi ymparikaantyminen-indeksissa})
            (do (log/debug (str "Siirretään K:ta eteenpäin kohti indeksiä: " (inc uusi-k-indeksi)))
                (recur (inc uusi-k-indeksi)
                       (if ymparikaantyminen-indeksissa
                         ymparikaantyminen-indeksissa ;; Ympärikääntyminen havaittiin aiemmin
                         (etsi-kn-tormays-nykyiseen-sijaintiin {:kaikki-merkinnat kaikki-merkinnat
                                                                :kasiteltava-indeksi kasiteltava-indeksi
                                                                :seuraava-merkinta seuraava-merkinta
                                                                :uusi-k-indeksi uusi-k-indeksi
                                                                :uusi-k-sijainti uusi-k-sijainti}))))))))))

(defn- yrita-maarittaa-ensimmainen-k
  "Määrittää K:n ensimmäiseen merkintään, jolla on sijainti, kun seuraava merkintä
   on vähintään M metrin päässä tästä merkinnästä."
  [kaikki-merkinnat seuraava-merkinta m]
  (let [ensimmainen-sijainti (:sijainti (first (remove #(nil? (:sijainti %)) kaikki-merkinnat)))
        seuraava-sijainti (:sijainti seuraava-merkinta)]
    (when (and ensimmainen-sijainti seuraava-sijainti
               (>= (math/pisteiden-etaisyys ensimmainen-sijainti
                                            seuraava-sijainti)
                   m))
      (log/debug "Määritetään K ensimmäiseen pisteeseen")
      {:sijainti (:sijainti ensimmainen-sijainti)
       :indeksi 0})))

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
   ympärikääntyminen tapahtui (sijainti, jossa alettiin lähestyä K:ta).

   On mahdollista, ettei uutta K:ta saada määritettyä, jos merkintöjen sijainti puuttuu."
  [{:keys [tulos seuraava-merkinta kasiteltava-indeksi k-indeksi m kaikki-merkinnat]}]
  (log/debug "Käsitellään merkintä indeksissä: " kasiteltava-indeksi)
  (if (nil? (:k-indeksi tulos))
    ;; K:ta ei ole vielä määritelty, määrittele se ensimmäiseen pisteeseen
    ;; kunhan nykyinen käsiteltävä piste on riittävän kaukana
    (yrita-maarittaa-ensimmainen-k kaikki-merkinnat seuraava-merkinta m)
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
                  (let [uusi-k (maarita-uusi-k {:kaikki-merkinnat merkinnat
                                                :tulos tulos
                                                :kasiteltava-indeksi (:kasiteltava-indeksi tulos)
                                                :seuraava-merkinta seuraava
                                                :k-indeksi (:k-indeksi tulos)
                                                :m m})]
                    {:kasiteltava-indeksi (inc (:kasiteltava-indeksi tulos))
                     ;; Jos uusi K saatiin määritettyä, käytä sitä, muuten ota olemassa oleva
                     :k-indeksi (or (:indeksi uusi-k) (:k-indeksi tulos))
                     :k-sijainti (or (:sijainti uusi-k) (:k-sijainti tulos))
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
  (let [ymparikaantymiset (set (analysoi-ymparikaantymiset merkinnat 120))
        merkinnat (vec (map-indexed (fn [index merkinta]
                                      (if (ymparikaantymiset index)
                                        (assoc merkinta :ymparikaantyminen? true)
                                        merkinta))
                                    merkinnat))]
    merkinnat))