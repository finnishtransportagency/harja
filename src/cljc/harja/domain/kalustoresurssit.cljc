(ns harja.domain.kalustoresurssit
  "Suunnittelun kalustoresurssien hoitoluokkaryhmät MHU26-urakoille.")

(def hoitoluokkaryhmat
  "Kalustoresurssien syötössä käytetyt hoitoluokkaryhmät.
   :avain on tietokantaan tallennettava tunniste, :nimi käyttöliittymässä näytettävä teksti."
  [{:avain "ise-ib" :nimi "Ise–Ib"}
   {:avain "ic-iii" :nimi "Ic–III"}
   {:avain "k1-k2-l" :nimi "K1, K2 ja L"}])

(def hoitoluokkaryhma-avaimet
  "Sallitut hoitoluokkaryhmien tunnisteet."
  (into #{} (map :avain) hoitoluokkaryhmat))

(defn validi-hoitoluokkaryhma?
  "Onko annettu tunniste sallittu hoitoluokkaryhmä."
  [avain]
  (contains? hoitoluokkaryhma-avaimet avain))

(def hoitoluokka->ryhma
  "Mäppäys yksittäisestä talvihoitoluokasta (talvihoito-merkkijono) kalustoresurssien
   hoitoluokkaryhmän tunnisteeseen. Huoltoaukot-luokat (Talvihoito, Hoito osin, Ei
   talvihoitoa) eivät kuulu mihinkään ryhmään eikä niitä huomioida laskennassa."
  {"Ise" "ise-ib"
   "Is"  "ise-ib"
   "I"   "ise-ib"
   "Ib"  "ise-ib"
   "Ic"  "ic-iii"
   "II"  "ic-iii"
   "III" "ic-iii"
   "L"   "k1-k2-l"
   "K1"  "k1-k2-l"
   "K2"  "k1-k2-l"})

(def ^:private ryhma-jarjestys
  "Hoitoluokkaryhmien tunnisteet järjestyksessä. Käytetään tasapelin ratkaisemiseen:
   tasapelin sattuessa valitaan ensimmäinen tässä järjestyksessä."
  (mapv :avain hoitoluokkaryhmat))

(defn reitin-hallitseva-ryhma
  "Palauttaa sen hoitoluokkaryhmän tunnisteen, jota reitillä on pituudeltaan (km) eniten.
   Tasapelin sattuessa valitaan ensimmäinen hoitoluokkaryhmien järjestyksessä.
   Palauttaa nil, jos reitillä ei ole yhtään tarjouksessa esiintyvää hoitoluokkaryhmää.
   sijainnit: sekvenssi karttoja, joissa :hoitoluokka (talvihoito-merkkijono) ja :laskettu_pituus."
  [sijainnit]
  (let [pituus-ryhmittain (reduce (fn [acc {:keys [hoitoluokka laskettu_pituus]}]
                                    (if-let [ryhma (hoitoluokka->ryhma hoitoluokka)]
                                      (update acc ryhma (fnil + 0) (or laskettu_pituus 0))
                                      acc))
                            {}
                            sijainnit)]
    (when (seq pituus-ryhmittain)
      (->> ryhma-jarjestys
        (map-indexed (fn [idx ryhma] [ryhma idx (get pituus-ryhmittain ryhma 0)]))
        (filter (fn [[_ _ pituus]] (pos? pituus)))
        (sort-by (fn [[_ idx pituus]] [(- pituus) idx]))
        ffirst))))

(defn reitin-kalusto-kpl
  "Reitin koko kalustomäärä kappaleina (traktorit + kuorma-autot + kuppi-kuormaajat)."
  [{:keys [tr_maara ka_maara kup_maara]}]
  (+ (or tr_maara 0) (or ka_maara 0) (or kup_maara 0)))

(defn reittien-kalusto-ryhmittain
  "Summaa reiteille suunnitellun kaluston hoitoluokkaryhmittäin. Kunkin reitin koko
   kalustomäärä kohdistetaan reitin hallitsevalle hoitoluokkaryhmälle.
   reitit: talvihoitoreitit, joissa :reitit (sijainnit) sekä :tr_maara/:ka_maara/:kup_maara.
   Palauttaa kartan {ryhmän-avain kalusto-kpl}."
  [reitit]
  (reduce (fn [acc reitti]
            (if-let [ryhma (reitin-hallitseva-ryhma (:reitit reitti))]
              (update acc ryhma (fnil + 0) (reitin-kalusto-kpl reitti))
              acc))
    {}
    reitit))

(defn kokoa-kalustoyhteenveto
  "Kokoaa kalustoyhteenvedon taulukon rivit. Rivit muodostetaan vain niistä
   hoitoluokkaryhmistä, jotka ovat urakalla käytössä Suunnittelu / Kalustoresurssit -sivulla.
   luvatut-kalustoresurssit: sekvenssi {:hoitoluokkaryhma avain :maara n}.
   reitit: urakan talvihoitoreitit.
   Palauttaa vektorin karttoja {:hoitoluokkaryhma :nimi :luvattu :suunniteltu}."
  [luvatut-kalustoresurssit reitit]
  (let [luvattu-ryhmittain (into {} (map (juxt :hoitoluokkaryhma :maara)) luvatut-kalustoresurssit)
        kaytossa-olevat-avaimet (set (keys luvattu-ryhmittain))
        suunniteltu-ryhmittain (reittien-kalusto-ryhmittain reitit)]
    (->> hoitoluokkaryhmat
      (filter (comp kaytossa-olevat-avaimet :avain))
      (mapv (fn [{:keys [avain nimi]}]
              {:hoitoluokkaryhma avain
               :nimi nimi
               :luvattu (get luvattu-ryhmittain avain)
               :suunniteltu (get suunniteltu-ryhmittain avain 0)})))))
