(ns harja.domain.laadunseuranta.sanktion-lomake
  "Sanktiolomakkeen puhtaat päätökset: näkyvyys, tilamuunnokset ja tallennusmuoto.
  Ei lue atomeja eikä kutsu palvelua. Atomit luetaan näkymässä ja annetaan tänne arvoina;
  lopullisen määrän ja sallivuuden ratkaisee palvelu."
  (:require [clojure.string :as str]
            [harja.pvm :as pvm]
            [harja.domain.laadunseuranta.sanktion-laskenta :as sanktion-laskenta]
            [harja.domain.laadunseuranta.sanktion-summamaaritys :as summamaaritys]
            [harja.domain.laadunseuranta.sanktio :as sanktio-domain]
            [harja.domain.laadunseuranta.sanktiotyyppi :as sanktiotyyppi-domain]))

(defn- pvm-hoitokauden-alkuvuosi
  [pvm]
  (pvm/hoitokauden-alkuvuosi #?(:clj (pvm/joda-timeksi pvm)
                                :cljs pvm)))

(defn- viimeinen-hoitokausi-nykyhetkella?
  "Tosi, kun annettu päivämäärä osuu urakan viimeiseen hoitovuoteen. Nil, jos jompikumpi päivämäärä puuttuu."
  [urakan-loppupvm pvm]
  (when (and urakan-loppupvm pvm)
    (= (dec (pvm/vuosi urakan-loppupvm))
       (pvm-hoitokauden-alkuvuosi pvm))))

(defn talvisuolan-validointi
  "Palauttaa virheviestin, kun talvisuolan ylityssanktion päivämäärä ei osu urakan viimeiseen
  hoitovuoteen. Muille lajeille sekä puuttuvalle urakan loppupäivälle tai päivämäärälle palauttaa nil."
  [urakan-loppupvm arvo sanktio]
  (when (and urakan-loppupvm
          (some? arvo)
          (= :talvisuolan_ylitys (:laji sanktio))
          (not (viimeinen-hoitokausi-nykyhetkella? urakan-loppupvm arvo)))
    (str "Sanktio voidaan määrätä ainostaan urakan viimeiselle hoitovuodelle "
      (pvm/vuosi urakan-loppupvm) ".")))

(defn aseta-omailmoitus
  "Asettaa omailmoituksen ja päivittää kiinteän sanktiosumman sen mukaiseksi.
  Summa lasketaan aina normaalimäärästä, joten toistuva kutsu ei puolita summaa uudelleen.
  Jos riviltä puuttuu sekä normaalimäärä että summa, vain omailmoituksen tila asetetaan."
  [rivi arvo]
  (let [omailmoitettu (boolean arvo)
        normaalimaara (or (:normaalimaara rivi) (:summa rivi))]
    (cond-> (assoc rivi :omailmoitettu omailmoitettu)
      normaalimaara
      (assoc :normaalimaara normaalimaara
        :summa (summamaaritys/kiintea-sanktiosumma normaalimaara omailmoitettu)))))

(defn sanktion-tallennusmuoto
  "Muodostaa tallennettavan sanktion lomakkeen tiedoista.
  Kiinteän summamallin tiedot (normaalimäärä, omailmoitus, summa) yhdistetään sanktioon.
  Laskettavalla sanktiolla käyttäjän summa poistetaan ja tilalle asetetaan laskennan syöte;
  lopullisen määrän ratkaisee palvelu.
  `muokattu` on lomakkeen nykyinen sanktio, `summakonteksti` on `sanktion-summakonteksti`n tulos
  ja `lomakkeen-tiedot` on sanktio ilman lomakkeen sisäisiä tilatietoja."
  [muokattu {:keys [kiintea-summamalli laskettu-maaritys]} lomakkeen-tiedot]
  (cond-> lomakkeen-tiedot
    kiintea-summamalli
    (merge kiintea-summamalli)
    laskettu-maaritys
    (-> (dissoc :summa :normaalimaara :laskennan-syote)
      (assoc :omailmoitettu false
        :laskettava-syote (sanktion-laskenta/laskettava-syote muokattu)))))

(defn sanktion-summakonteksti
  "Ratkaisee aktiivisen sanktiotyypin summamäärityksen: palauttaa avaimet
  `:kiintea-profiilisumma?`, `:omailmoitus-sallittu?`, `:kiintea-summamalli`
  (normaalimäärä, omailmoitus ja summa tai nil), `:laskettu-maaritys` ja
  `:c-ryhman-maaritys` (manuaalisen viitesumman tiedot tai nil)."
  [{:keys [laji omailmoitettu aktiivinen-tyyppi]}]
  {:kiintea-profiilisumma? (summamaaritys/sanktiotyypilla-kiintea-automaattinen-summamaaritys? aktiivinen-tyyppi)
   :omailmoitus-sallittu? (boolean (:voi-puolittaa-omailmoituksella aktiivinen-tyyppi))
   :kiintea-summamalli (summamaaritys/kiintea-summamalli aktiivinen-tyyppi omailmoitettu)
   :laskettu-maaritys (sanktion-laskenta/laskettu-summamaaritys aktiivinen-tyyppi)
   :c-ryhman-maaritys (summamaaritys/c-ryhman-manuaalinen-maaritys laji aktiivinen-tyyppi)})

(declare hoitovuodet)

(defn aikakausikonteksti
  "Urakan alkuvuoden, hoitokauden ja urakkatyypin mukaiset lomakeehdot.
  Avaimet: `:nayta-indeksi?` (teiden hoito, alkuvuosi ≤ 2020), `:perinta-tapa`
  (`:laskutuskuukausi` alkuvuosi ≤ 2024, muuten `:hoitovuosi`), `:maaraystapa?` ja
  `:kasittelytavat` (MHU25+), `:salli-arvonvahennys?` (ei MHU25+ eikä hoitokaudesta 2026 alkaen).
  Kun urakan loppupäivä annetaan, mukana on myös `:hoitovuodet`, urakan hoitovuosien alkuvuodet."
  ([urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi]
   (let [mhu25? (and (= :teiden-hoito urakkatyyppi)
                  (>= urakan-alkuvuosi 2025))]
     {:nayta-indeksi? (and (= :teiden-hoito urakkatyyppi)
                        (<= urakan-alkuvuosi 2020))
      :perinta-tapa (if (<= urakan-alkuvuosi 2024)
                      :laskutuskuukausi
                      :hoitovuosi)
      :maaraystapa? mhu25?
      :kasittelytavat (if mhu25? sanktio-domain/kasittelytavat-mhu25 sanktio-domain/kasittelytavat)
      :salli-arvonvahennys? (and (not mhu25?)
                              (< hoitokauden-alkuvuosi 2026))}))
  ([urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi urakan-loppupvm]
   (assoc (aikakausikonteksti urakan-alkuvuosi hoitokauden-alkuvuosi urakkatyyppi)
     :hoitovuodet (hoitovuodet urakan-alkuvuosi urakan-loppupvm))))

(defn sallitut-sanktiolajit
  "Arvonvähennys kuuluu urakan sanktioihin, mutta ei sanktiolomakkeelle silloin, kun sille on oma lomake
  (MHU25+ ja hoitovuodesta 2026 alkaen)."
  [{:keys [salli-arvonvahennys?]} sanktiolajit]
  (if salli-arvonvahennys?
    sanktiolajit
    (vec (remove #(= :arvonvahennyssanktio %) sanktiolajit))))

(defn lukutila?
  "Laatupoikkeaman kautta tehdyn sanktion lukutila välitetään sanktion tiedoissa.
  Suorasanktion lukutila tulee aina lomakkeen parametrista."
  [suorasanktio? sanktio lukutila-parametri]
  (boolean
    (if (and (not suorasanktio?) (:lukutila? sanktio))
      (:lukutila? sanktio)
      lukutila-parametri)))

(def nakymakontekstin-avaimet
  "Sanktiolomakkeen kenttäkuvausfunktioiden yhteinen konteksti. Arvo voi olla nil, avain ei saa puuttua."
  [:sanktio :lomake :aikakausi :summa :lukutila? :voi-muokata?
   :sanktiolajit :tyyppi-valinnat :urakan-alkuvuosi :toimenpideinstanssit
   :kulun-kohdistukset :hoidonjohtopalkkio-tr :laskutuskuukaudet :laskutuskuukausi-id
   :indeksi-kaytossa? :urakan-indeksi])

(defn nakymakonteksti
  "Palauttaa kontekstin sellaisenaan tai heittää virheen, jos jokin avain puuttuu.
  Estää kirjoitusvirheen muuttumisen hiljaa nil-arvoksi kenttäkuvauksessa."
  [konteksti]
  (let [puuttuvat (vec (remove #(contains? konteksti %) nakymakontekstin-avaimet))]
    (when (seq puuttuvat)
      (throw (ex-info (str "Sanktiolomakkeen kontekstista puuttuu avaimet: " (pr-str puuttuvat))
               {:puuttuvat puuttuvat})))
    konteksti))

;; Hoitovuosi tunnistetaan sen alkuvuodella (hoitovuosi alkaa 1.10.).
(defn hoitovuodet
  "Urakan hoitovuosien alkuvuodet urakan alkuvuodesta viimeiseen urakan loppupäivään osuvaan hoitovuoteen."
  [urakan-alkuvuosi urakan-loppupvm]
  (if-not urakan-loppupvm
    []
    (let [viimeinen (if (>= (pvm/kuukausi urakan-loppupvm) 10)
                    (pvm/vuosi urakan-loppupvm)
                    (dec (pvm/vuosi urakan-loppupvm)))]
      (vec (range urakan-alkuvuosi (inc viimeinen))))))

(defn hoitovuosi-tekstina
  "Muotoilee hoitovuoden alkuvuoden tekstiksi, esim. `1. hoitovuosi (2026 - 2027)`.
  Palauttaa nil, jos hoitovuosi ei ole kokonaisluku."
  [urakan-alkuvuosi hoitovuosi]
  (when (int? hoitovuosi)
    (str (inc (- hoitovuosi urakan-alkuvuosi)) ". hoitovuosi (" hoitovuosi " - " (inc hoitovuosi) ")")))

(defn perintapvm-hoitovuodeksi
  "Palauttaa perintäpäivän hoitovuoden alkuvuoden, jos se kuuluu annettuihin hoitovuosiin."
  [hoitovuodet perintapvm]
  (when perintapvm
    (let [hoitovuosi (pvm/hoitokauden-alkuvuosi (pvm/vuosi perintapvm) (pvm/kuukausi perintapvm))]
      (when (some #{hoitovuosi} hoitovuodet)
        hoitovuosi))))

(defn hoidonjohtopalkkio-tehtavaryhma
  "MHU25+ sanktio kohdistuu tehtäväryhmään G - Hoidonjohtopalkkio.
  Palauttaa tehtäväryhmän nimen perusteella tai nil. Nimihaku korvataan vakaalla tunnisteella tiketissä T09."
  [tehtavaryhmat]
  (some #(when (= "G - Hoidonjohtopalkkio" (:tehtavaryhma_nimi %)) %) tehtavaryhmat))

(defn aseta-kasittely-tai-maarattypvm
  "Asettaa käsittelyajan ja määräyspäivän sanktioon.
  Perintäpäivä päivitetään vain, jos sanktiolla ei ole laskutuskuukauden komponenttitietoja:
  `:maarattypvm`-perintätavalla se on määräpäivän hoitokauden loppupäivä, muuten annettu päivämäärä.
  Puuttuva päivämäärä (nil) tyhjentää arvot eikä aiheuta virhettä."
  [sanktio arvo perinta-tapa]
  (let [maarattypvm? (= :maarattypvm perinta-tapa)
        perintapvm (when-not (:laskutuskuukausi-komp-tiedot sanktio)
                     (if maarattypvm?
                       (when arvo
                         (pvm/hoitokauden-loppupvm
                           (pvm/vuosi
                             (second (pvm/paivamaaran-hoitokausi arvo)))))
                       arvo))]
    (assoc-in
      (cond-> (assoc sanktio :maarattypvm arvo)
        (nil? (:laskutuskuukausi-komp-tiedot sanktio))
        (assoc :perintapvm perintapvm))
      [:laatupoikkeama :paatos :kasittelyaika]
      arvo)))

(defn mahdolliset-kulun-kohdistukset
  "Palauttaa sanktiotyypille valittavat kohdistukset urakan alkuvuoden perusteella.
  Vuodesta 2025 alkaen kohdistus rajataan hoidon johtoon; sitä vanhemmilla urakoilla
  vaihtoehdot suodatetaan sanktiotyypin nimen perusteella."
  [toimenpideinstanssit urakan-alkuvuosi sanktion-tyyppi]
  (if (> urakan-alkuvuosi 2024)
    (filter #(= "23150" (:t2_koodi %)) toimenpideinstanssit)
    (case sanktion-tyyppi
      "Muut hoitourakan tehtäväkokonaisuudet" (remove
                                                #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                                                toimenpideinstanssit)
      "Talvihoito, päätiet" (filter
                              #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                              toimenpideinstanssit)
      "Talvihoito, muut tiet" (filter
                                #(str/includes? (str/lower-case (:tpi_nimi %)) "talvi")
                                toimenpideinstanssit)
      "Sorateiden hoito ja ylläpito" (filter
                                       #(or (str/includes? (str/lower-case (:tpi_nimi %)) "soratie")
                                          (str/includes? (str/lower-case (:tpi_nimi %)) "sorateiden"))
                                       toimenpideinstanssit)
      "Liikenneympäristön hoito" (filter
                                   #(str/includes? (str/lower-case (:tpi_nimi %)) "liikenne")
                                   toimenpideinstanssit)
      toimenpideinstanssit)))

(defn- tyypin-toimenpideinstanssi
  [tyyppi toimenpideinstanssit]
  (when-some [toimenpidekoodi (:toimenpidekoodi tyyppi)]
    (some #(when (= toimenpidekoodi (:id %)) (:tpi_id %))
      toimenpideinstanssit)))

(defn- ainoa-kulun-kohdistus
  [kulun-kohdistukset]
  (when (= 1 (count kulun-kohdistukset))
    (:tpi_id (first kulun-kohdistukset))))

(defn vaihda-sanktiolajia
  "Vaihtaa sanktion lajin ja ratkaisee tyypin ja toimenpideinstanssin uuden lajin tyypeistä.
  Aiempi sallittu tyyppi säilytetään, muuten valitaan ainoa sallittu tyyppi.
  Valitun tyypin toimenpideinstanssi ratkaistaan toimenpidekoodin perusteella tai koodittomalle
  tyypille uudemman urakan ainoasta kulun kohdistuksesta.
  Jos tyyppiä ei voida valita, se tyhjenee ja laskennan syötteet poistetaan.
  Muistutusta lukuun ottamatta tyypittömän rivin summa, omailmoitus, toimenpideinstanssi ja indeksi nollataan."
  ([rivi uusi-laji sanktiotyypit toimenpideinstanssit]
   (vaihda-sanktiolajia rivi uusi-laji sanktiotyypit toimenpideinstanssit nil nil))
  ([rivi uusi-laji sanktiotyypit toimenpideinstanssit urakan-alkuvuosi kulun-kohdistukset]
   (let [vanha-tyyppi-id (get-in rivi [:tyyppi :id])
         rivi (assoc rivi :laji uusi-laji :tyyppi nil)
         vanha-tyyppi (when (some? vanha-tyyppi-id)
                        (some #(when (= vanha-tyyppi-id (:id %)) %) sanktiotyypit))
         tyyppi (or vanha-tyyppi
                  (when (= 1 (count sanktiotyypit)) (first sanktiotyypit)))
         rivi (if tyyppi
                (assoc rivi
                  :tyyppi tyyppi
                  :toimenpideinstanssi (if (some? (:toimenpidekoodi tyyppi))
                                         (tyypin-toimenpideinstanssi tyyppi toimenpideinstanssit)
                                         (when (and urakan-alkuvuosi (> urakan-alkuvuosi 2024))
                                           (ainoa-kulun-kohdistus kulun-kohdistukset))))
                (dissoc rivi :laskettava-syote :laskennan-syote))]
     (if (and (not (sanktio-domain/muu-kuin-muistutus? rivi))
           (nil? (:tyyppi rivi)))
       (assoc rivi
         :summa nil
         :omailmoitettu false
         :toimenpideinstanssi nil
         :indeksi nil)
       rivi))))

(defn vaihda-sanktiotyyppia
  "Vaihtaa sanktion tyypin ja päivittää tyypistä riippuvat summatiedot.
  Kiinteällä automaattisella summalla asetetaan normaalimäärä ja omailmoituksen mukainen summa.
  C-ryhmän manuaalisella summalla ja muilla tyypeillä aiempi summa poistetaan tai nollataan.
  Laskennan syötteet poistetaan, kun tyyppi vaihtuu. Toimenpideinstanssi päätellään tyypin
  toimenpidekoodista tai koodittomalle tyypille ainoasta kulun kohdistuksesta."
  [rivi uusi-tyyppi urakan-alkuvuosi toimenpideinstanssit kulun-kohdistukset]
  (let [tyyppi-vaihtui? (not= (get-in rivi [:tyyppi :id]) (:id uusi-tyyppi))
        rivi (if (<= urakan-alkuvuosi 2024)
               (let [toimenpidekoodi (:toimenpidekoodi uusi-tyyppi)
                     toimenpideinstanssi (cond
                                           toimenpidekoodi
                                           (tyypin-toimenpideinstanssi uusi-tyyppi toimenpideinstanssit)
                                           (= 1 (count kulun-kohdistukset))
                                           (:tpi_id (first kulun-kohdistukset))
                                           :else (:toimenpideinstanssi rivi))]
                 (assoc rivi
                   :tyyppi uusi-tyyppi
                   :toimenpideinstanssi toimenpideinstanssi
                   :tpi_id toimenpideinstanssi))
               (let [toimenpidekoodi (:toimenpidekoodi uusi-tyyppi)
                     kohdistus (ainoa-kulun-kohdistus kulun-kohdistukset)]
                 (cond-> (assoc rivi :tyyppi uusi-tyyppi)
                   (and (some? uusi-tyyppi)
                     (or (some? toimenpidekoodi) (some? kohdistus)))
                   (assoc :toimenpideinstanssi
                     (if (some? toimenpidekoodi)
                       (tyypin-toimenpideinstanssi uusi-tyyppi toimenpideinstanssit)
                       kohdistus)))))
        kiintea-summa (summamaaritys/sanktiotyypin-kiintea-automaattinen-summa uusi-tyyppi)
        manuaalinen-c? (some? (summamaaritys/c-ryhman-manuaalinen-maaritys (:laji rivi) uusi-tyyppi))
        omailmoitettu (if kiintea-summa
                        (:omailmoitettu rivi)
                        false)
        rivi (if (or (not tyyppi-vaihtui?)
                   (contains? rivi :omailmoitettu)
                   kiintea-summa)
               (assoc rivi :omailmoitettu omailmoitettu)
               rivi)]
    (cond-> rivi
      tyyppi-vaihtui?
      (dissoc :laskettava-syote :laskennan-syote)
      (and tyyppi-vaihtui? kiintea-summa)
      (assoc :normaalimaara kiintea-summa
        :summa (summamaaritys/kiintea-sanktiosumma kiintea-summa omailmoitettu))
      (and tyyppi-vaihtui? manuaalinen-c?)
      (dissoc :normaalimaara :summa)
      (and tyyppi-vaihtui? (nil? kiintea-summa) (not manuaalinen-c?))
      (as-> rivi
        (cond-> rivi
          (contains? rivi :normaalimaara) (assoc :normaalimaara nil)
          (contains? rivi :summa) (assoc :summa nil))))))

(defn sanktion-lomakekonteksti
  "Ratkaisee sanktiolomakkeen kenttien nykyisen näkyvyyden ja validoinnin.

  Syötteessä annetaan sanktio, urakkatyypin boolean-konteksti,
  aikakausikonteksti sekä tarvittaessa urakan loppupäivä. Paluuarvo sisältää
  vain lomakkeen domain-päätökset; kohdistus- ja tunnistepäätökset eivät kuulu
  tähän funktioon. Manuaalinen summakenttä näytetään vasta tyypin valinnan jälkeen,
  jos lomakkeella on tyyppikenttä."
  [{:keys [sanktio aktiivinen-tyyppi yllapitourakka? yllapitokohdeurakka?
           vesivaylaurakka? laskutus-ilman-laskutuskelpoisuutta?
           aikakausikonteksti urakan-loppupvm]
    :or {sanktio {}}}]
  (let [laji (:laji sanktio)
        tyyppi (or aktiivinen-tyyppi (:tyyppi sanktio))
        asiakirjamerkinta? (= :asiakirjamerkintojen_paikkansa_pitamattomyys laji)
        laskutusrajan-ylitys? (= :laskutus_yli_laskutusrajan laji)
        laskutus-ilman-laskutuskelpoisuutta? (or laskutus-ilman-laskutuskelpoisuutta?
                                               (= :laskutus_ilman_laskutuskelpoisuutta laji))
        perusteluton? (sanktiotyyppi-domain/perusteluton-sanktio? laji tyyppi)
        nayta-tyyppi? (not (or yllapitourakka? vesivaylaurakka?
                             asiakirjamerkinta?
                             laskutus-ilman-laskutuskelpoisuutta?
                             laskutusrajan-ylitys?))]
    {:vesivaylaurakka? vesivaylaurakka?
     :yllapitourakka? yllapitourakka?
     :laskutus-ilman-laskutuskelpoisuutta? laskutus-ilman-laskutuskelpoisuutta?
     :nayta-tyyppi? nayta-tyyppi?
     :nayta-tapahtumapaikka? (not (or yllapitokohdeurakka? vesivaylaurakka?
                                    laskutus-ilman-laskutuskelpoisuutta?
                                    laskutusrajan-ylitys?))
     :nayta-havaittu? (not laskutus-ilman-laskutuskelpoisuutta?)
     :nayta-laskutuskuukausi-teksti? (not yllapitourakka?)
     :nayta-indeksi? (and (:nayta-indeksi? aikakausikonteksti)
                       (not= :muistutus laji))
     :nayta-manuaalinen-summa? (and (not= :muistutus laji)
                                 (not= :laskutus_yli_laskutusrajan laji)
                                 (or (not nayta-tyyppi?) (some? (:id tyyppi))))
     :nayta-perustelu? (not perusteluton?)
     :nayta-puute-tai-laiminlyonti? yllapitourakka?
     :nayta-talvisuola-ilmoitus? (= :talvisuolan_ylitys laji)
     :salli-arvonvahennys? (:salli-arvonvahennys? aikakausikonteksti)
     :talvisuolan-validointi (fn [arvo]
                               (talvisuolan-validointi urakan-loppupvm arvo sanktio))}))

(defn aktiivinen-sanktiotyyppi
  "Palauttaa sanktiotyyppien joukosta sen, jonka id on annettu, tai nil."
  [sanktiotyypit tyyppi-id]
  (some #(when (= tyyppi-id (:id %)) %) sanktiotyypit))

(defn kokoa-nakymakonteksti
  "Kokoaa sanktiolomakkeen kenttäkuvausten yhteisen kontekstin valmiiksi luetuista arvoista.
  Ei lue atomeja. Paluuarvo täyttää `nakymakonteksti`-funktion avainvaatimukset.

  Syöte: sanktio, urakka (:alkupvm :loppupvm :tyyppi :indeksi), hoitokauden-alkuvuosi,
  yllapitourakka?, yllapitokohdeurakka?, vesivaylaurakka?, sanktiolajit, sanktiotyypit
  (valitun lajin tyypit), toimenpideinstanssit, kulun-kohdistukset, tehtavaryhmat,
  laskutuskuukaudet, laskutuskuukausi-id, indeksi-kaytossa?, lukutila? ja voi-muokata?."
  [{:keys [sanktio urakka hoitokauden-alkuvuosi yllapitourakka? yllapitokohdeurakka?
           vesivaylaurakka? sanktiolajit sanktiotyypit toimenpideinstanssit
           kulun-kohdistukset tehtavaryhmat laskutuskuukaudet laskutuskuukausi-id
           indeksi-kaytossa? voi-muokata?]
    lukutila-parametri :lukutila?}]
  (let [urakan-alkuvuosi (pvm/vuosi (:alkupvm urakka))
        urakan-loppupvm (:loppupvm urakka)
        aikakausi (aikakausikonteksti urakan-alkuvuosi hoitokauden-alkuvuosi
                    (:tyyppi urakka) urakan-loppupvm)
        sanktiotyypit (vec sanktiotyypit)
        aktiivinen-tyyppi (aktiivinen-sanktiotyyppi sanktiotyypit (get-in sanktio [:tyyppi :id]))
        laskutus-ilman-laskutuskelpoisuutta? (= :laskutus_ilman_laskutuskelpoisuutta (:laji sanktio))]
    (nakymakonteksti
      {:sanktio sanktio
       :lomake (sanktion-lomakekonteksti
                 {:sanktio sanktio
                  :aktiivinen-tyyppi aktiivinen-tyyppi
                  :yllapitourakka? yllapitourakka?
                  :yllapitokohdeurakka? yllapitokohdeurakka?
                  :vesivaylaurakka? vesivaylaurakka?
                  :laskutus-ilman-laskutuskelpoisuutta? laskutus-ilman-laskutuskelpoisuutta?
                  :aikakausikonteksti aikakausi
                  :urakan-loppupvm urakan-loppupvm})
       :aikakausi aikakausi
       :summa (sanktion-summakonteksti
                {:laji (:laji sanktio)
                 :omailmoitettu (:omailmoitettu sanktio)
                 :aktiivinen-tyyppi aktiivinen-tyyppi})
       :lukutila? (lukutila? (:suorasanktio sanktio) sanktio lukutila-parametri)
       :voi-muokata? voi-muokata?
       :sanktiolajit (sallitut-sanktiolajit aikakausi sanktiolajit)
       :tyyppi-valinnat sanktiotyypit
       :urakan-alkuvuosi urakan-alkuvuosi
       :toimenpideinstanssit toimenpideinstanssit
       :kulun-kohdistukset kulun-kohdistukset
       :hoidonjohtopalkkio-tr (hoidonjohtopalkkio-tehtavaryhma tehtavaryhmat)
       :laskutuskuukaudet laskutuskuukaudet
       :laskutuskuukausi-id laskutuskuukausi-id
       :indeksi-kaytossa? indeksi-kaytossa?
       :urakan-indeksi (:indeksi urakka)})))
