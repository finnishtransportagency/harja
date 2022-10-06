(ns harja.domain.lupaus-domain
  (:require [harja.pvm :as pvm]
            [clojure.set :as set]
            [harja.domain.roolit :as roolit]))

(defn numero->kirjain [numero]
  (case numero
    1 "A"
    2 "B"
    3 "C"
    4 "D"
    5 "E"
    nil))

(def ennusteiden-tilat
  #{;; "Ei vielä ennustetta"
    ;; Ensimmäiset ennusteet annetaan lokakuun alussa, kun tiedot on syötetty ensimmäiseltä  kuukaudelta.
    :ei-viela-ennustetta

    ;; "Huhtikuun ennusteen mukaan urakalle on tulossa Bonusta 5200 €"
    ;; Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.
    :ennuste

    ;; "Toteuman mukaan urakalle on tulossa Bonusta 5200 €"
    ;; Lopulliset bonukset ja sanktiot sovitaan välikatselmuksessa.
    :alustava-toteuma

    ;; "Urakalle tuli bonusta 1. hoitovuotena 5200 €"
    ;; Tiedot on käyty läpi välikatselmuksessa.
    :katselmoitu-toteuma})

(defn yksittainen? [lupaus]
  (= "yksittainen" (:lupaustyyppi lupaus)))

(defn hylatyt [vastaukset]
  (filter #(false? (:vastaus %)) vastaukset))

(defn hyvaksytyt [vastaukset]
  (filter #(true? (:vastaus %)) vastaukset))

(defn paatokset [vastaukset]
  (filter :paatos vastaukset))

(defn hylatty? [vastaukset joustovara-kkta]
  (> (count (hylatyt vastaukset)) joustovara-kkta))

(defn hyvaksytty? [vastaukset joustovara-kkta paatos-kk]
  (let [vastaus-kuukaudet-lkm (if (= paatos-kk 0)
                                12                          ; 0 = kaikki
                                1)
        vaaditut-hyvaksynnat (- vastaus-kuukaudet-lkm joustovara-kkta)
        hyvaksynnat (count (hyvaksytyt vastaukset))]
    (>= hyvaksynnat vaaditut-hyvaksynnat)))

(defn paatos-hyvaksytty? [vastaukset joustovara-kkta paatos-kk]
  (hyvaksytty? (paatokset vastaukset) joustovara-kkta paatos-kk))

(defn paatos-hylatty? [vastaukset joustovara-kkta]
  (hylatty? (paatokset vastaukset) joustovara-kkta))

(defn vastattu? [{:keys [lupaus-vaihtoehto-id vastaus]}]
  (or (number? lupaus-vaihtoehto-id) (boolean? vastaus)))

(defn viimeisin-vastaus [vastaukset]
  (->> vastaukset
       (filter vastattu?)
       (sort-by (fn [{:keys [vuosi kuukausi]}]
                  [vuosi kuukausi]))
       last))

(defn yksittainen->toteuma [{:keys [vastaukset joustovara-kkta pisteet paatos-kk]}]
  (cond (paatos-hylatty? vastaukset joustovara-kkta)
        0

        (paatos-hyvaksytty? vastaukset joustovara-kkta paatos-kk)
        pisteet

        :else
        nil))

(defn monivalinta->toteuma [{:keys [vastaukset]}]
  (when-let [paatos (viimeisin-vastaus (paatokset vastaukset))]
    (:pisteet paatos)))

(defn kysely->toteuma [lupaus]
  (monivalinta->toteuma lupaus))

(defn yksittainen->ennuste [{:keys [vastaukset joustovara-kkta pisteet]}]
  (if (hylatty? vastaukset joustovara-kkta)
    0
    pisteet))

(defn kysely->ennuste [{:keys [vastaukset kyselypisteet]}]
  (or (:pisteet (viimeisin-vastaus vastaukset))
      kyselypisteet))

(defn monivalinta->ennuste [{:keys [vastaukset kyselypisteet]}]
  (or (:pisteet (viimeisin-vastaus vastaukset))
      kyselypisteet))

(defn lupaus->ennuste [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->ennuste lupaus)
    "kysely" (kysely->ennuste lupaus)
    "monivalinta" (monivalinta->ennuste lupaus)))

(defn lupaus->toteuma [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->toteuma lupaus)
    "kysely" (monivalinta->toteuma lupaus)
    "monivalinta" (monivalinta->toteuma lupaus)))

(defn lupaus->ennuste-tai-toteuma [lupaus]
  (or (when-let [toteuma (lupaus->toteuma lupaus)]
        {:pisteet-toteuma toteuma
         ;; Jos päättävät kuukaudet on täytetty, niin ennuste == toteuma.
         ;; Liitetään sama luku ennuste-avaimen alle, niin on helpompi laskea ryhmäkohtainen ennuste,
         ;; jos ryhmässä on sekaisin ennustetta ja toteumaa.
         :pisteet-ennuste toteuma})
      (when-let [ennuste (lupaus->ennuste lupaus)]
        {:pisteet-ennuste ennuste})))

(defn liita-ennuste-tai-toteuma [lupaus]
  (-> lupaus
      (merge (lupaus->ennuste-tai-toteuma lupaus))))

(def hoitokuukausi->jarjestysnumero
  {10 1
   11 2
   12 3
   1  4
   2  5
   3  6
   4  7
   5  8
   6  9
   7  10
   8  11
   9  12})

(defn hoitokuukausi-ennen?
  "Hoitovuosi alkaa elokuusta, joten esimerkiksi lokakuu on ennen tammikuuta:
  ```
  (hoitokuukausi-ennen? 10 1)
  => true
  ```"
  [a b]
  (< (hoitokuukausi->jarjestysnumero a) (hoitokuukausi->jarjestysnumero b)))

(def kaikki-kuukaudet [10 11 12 1 2 3 4 5 6 7 8 9])

(defn hoitokuukaudet [alkuvuosi]
  (let [loppuvuosi (inc alkuvuosi)]
    [{:vuosi alkuvuosi :kuukausi 10}
     {:vuosi alkuvuosi :kuukausi 11}
     {:vuosi alkuvuosi :kuukausi 12}
     {:vuosi loppuvuosi :kuukausi 1}
     {:vuosi loppuvuosi :kuukausi 2}
     {:vuosi loppuvuosi :kuukausi 3}
     {:vuosi loppuvuosi :kuukausi 4}
     {:vuosi loppuvuosi :kuukausi 5}
     {:vuosi loppuvuosi :kuukausi 6}
     {:vuosi loppuvuosi :kuukausi 7}
     {:vuosi loppuvuosi :kuukausi 8}
     {:vuosi loppuvuosi :kuukausi 9}]))

(defn paatos-kk-joukko [paatos-kk]
  (if (= 0 paatos-kk)
    (set kaikki-kuukaudet)
    #{paatos-kk}))

(defn vaaditut-vastauskuukaudet [{:keys [kirjaus-kkt paatos-kk] :as lupaus} kuluva-kuukausi]
  (->>
    ;; Vaaditut vastauskuukaudet koko vuoden ajalta
    (set/union (set kirjaus-kkt)
               (paatos-kk-joukko paatos-kk))
    ;; Vaaditaan vain kuluvaa kuukautta ennen olevat kuukaudet
    (filter #(or
               (nil? kuluva-kuukausi)                       ; Ei määritelty, ei suodateta
               (hoitokuukausi-ennen? % kuluva-kuukausi)))
    set))

(defn puuttuvat-vastauskuukaudet [{:keys [lupaustyyppi joustovara-kkta kirjaus-kkt paatos-kk vastaukset] :as lupaus}
                                  kuluva-kuukausi]
  (let [vastaus-kkt (->> vastaukset
                         (filter vastattu?)
                         (map :kuukausi)
                         set)
        vaaditut-kkt (vaaditut-vastauskuukaudet lupaus kuluva-kuukausi)]
    (set/difference vaaditut-kkt vastaus-kkt)))

(defn odottaa-kannanottoa-kkt
  ([lupaus nykyhetki valittu-hoitokausi]
   (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi]
     (cond
       ;; Tuleviin hoitokausiin ei oteta kantaa
       (pvm/ennen? nykyhetki hk-alkupvm)
       []

       ;; Menneet hoitokaudet: ei määritetä kuluvaa kuukautta
       (pvm/jalkeen? nykyhetki hk-loppupvm)
       (odottaa-kannanottoa-kkt lupaus nil)

       ;; Kuluva hoitokausi lasketaan kuluvan kuukauden perusteella
       :else
       (odottaa-kannanottoa-kkt lupaus (pvm/kuukausi nykyhetki)))))
  ([lupaus kuluva-kuukausi]
   (if (lupaus->toteuma lupaus)
     ;; Jos toteuma voidaan laskea, niin lupaukseen ei tarvitse enää ottaa kantaa.
     []

     ;; Palautetaan vaaditut vastauskuukaudet
     (puuttuvat-vastauskuukaudet lupaus kuluva-kuukausi))))

(defn odottaa-kannanottoa?
  ([lupaus kuluva-kuukausi]
   (boolean (seq (odottaa-kannanottoa-kkt lupaus kuluva-kuukausi))))
  ([lupaus nykyhetki valittu-hoitokausi]
   (boolean (seq (odottaa-kannanottoa-kkt lupaus nykyhetki valittu-hoitokausi)))))

(defn vertaa-kuukausia [eka toka]
  (compare [(:vuosi eka) (:kuukausi eka)]
           [(:vuosi toka) (:kuukausi toka)]))

(defn vertaa-nykyhetkeen [nykyhetki ajanhetki]
  (case (vertaa-kuukausia nykyhetki ajanhetki)
    -1 :tuleva-kuukausi
    0 :kuluva-kuukausi
    1 :mennyt-kuukausi))

(defn lupaus->kuukaudet
  "Palauttaa hoitovuoden 12 kuukautta muodossa:
  {:kuukausi 10,
   :odottaa-kannanottoa? false,
   :paattava-kuukausi? true,
   :nykyhetkeen-verrattuna :mennyt-kuukausi,
   :vastaus true}"
  [{:keys [paatos-kk vastaukset kirjaus-kkt joustovara-kkta] :as lupaus}
   nykyhetki valittu-hoitokausi]
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi
        kuluva-vuosi (pvm/vuosi nykyhetki)
        kuluva-kuukausi (pvm/kuukausi nykyhetki)
        kk->vastaus (into {}
                          (map (fn [vastaus] [(:kuukausi vastaus) vastaus]))
                          vastaukset)
        puuttuvat-kkt (odottaa-kannanottoa-kkt lupaus nykyhetki valittu-hoitokausi)
        paatos-kkt (paatos-kk-joukko paatos-kk)
        kirjaus-kkt (set kirjaus-kkt)
        paatos-hylatty? (paatos-hylatty? vastaukset joustovara-kkta)]
    (for [{:keys [vuosi kuukausi]} (hoitokuukaudet (pvm/vuosi hk-alkupvm))]
      (let [vastaus (kk->vastaus kuukausi)]
        (merge
          {:vuosi vuosi
           :kuukausi kuukausi
           :odottaa-kannanottoa? (contains? puuttuvat-kkt kuukausi)
           :paatos-hylatty? paatos-hylatty?
           :paattava-kuukausi? (contains? paatos-kkt kuukausi)
           :kirjauskuukausi? (contains? kirjaus-kkt kuukausi)
           :nykyhetkeen-verrattuna (vertaa-nykyhetkeen {:vuosi kuluva-vuosi
                                                        :kuukausi kuluva-kuukausi}
                                                       {:vuosi vuosi
                                                        :kuukausi kuukausi})}
          (when vastaus
            {:vastaus vastaus}))))))

(defn liita-lupaus-kuukaudet [lupaus nykyhetki valittu-hoitokausi]
  (assoc lupaus :lupaus-kuukaudet
                (lupaus->kuukaudet lupaus nykyhetki valittu-hoitokausi)))

(defn liita-odottaa-kannanottoa [lupaus nykyhetki valittu-hoitokausi]
  (assoc lupaus :odottaa-kannanottoa?
                (odottaa-kannanottoa? lupaus nykyhetki valittu-hoitokausi)))

(defn lupaus->odottaa-kannanottoa [lupaus]
  (let [kannanotto-kpl (reduce (fn [yht rivi]
                                   (let [kpl (if-not (empty? (filter :odottaa-kannanottoa? (:lupaus-kuukaudet rivi)))
                                               1 0)]
                                     (+ kpl yht)))
                                 0
                                 lupaus)]
    kannanotto-kpl))

(defn lupaus->merkitseva-odottaa-kannanottoa
  "Muistutussähköposti haluaa tietää onko jäljellä pelkästään merkitseviä/päättäviä kuukausia jäljellä kannanotoissa.
  Mikäli näin on, niin sähköpostia ei tarvitse lähettää. Päätellään siis täällä, että montako lupausta odottaa merkitsevää/päättävää
  vastausta. Urakoitsijat eivät ole kiinnostuneita päättävistä/merkitsevistä kuukausista, vaan kaikista muista."
  [lupaus]
  (let [kannanotto-kpl (reduce (fn [yht rivi]
                                 (let [merkitsevat-lupauskuukaudet (filter :paattava-kuukausi? (:lupaus-kuukaudet rivi))
                                       kpl (if-not (empty? (filter :odottaa-kannanottoa? merkitsevat-lupauskuukaudet))
                                             1 0)]
                                   (+ kpl yht)))
                               0
                               lupaus)]
    kannanotto-kpl))

(defn rivit->summa
  "Jos jokaisella rivillä on numero annetun avaimen alla, palauta numeroiden summa.
  Muuten palauta nil."
  [rivit avain]
  (let [luvut (->> rivit
                   (map avain)
                   (filter number?))]
    (if (= (count luvut) (count rivit))
      (reduce + luvut)
      nil)))

(defn rivit->ennuste [rivit]
  (rivit->summa rivit :pisteet-ennuste))

(defn rivit->toteuma [rivit]
  (rivit->summa rivit :pisteet-toteuma))

(defn rivit->maksimipisteet [rivit]
  (rivit->summa rivit :pisteet-max))

(defn lupausryhmat->odottaa-kannanottoa [lupausryhmat]
  (rivit->summa lupausryhmat :odottaa-kannanottoa))

(defn lupausryhmat->merkitsevat-odottaa-kannanottoa [lupausryhmat]
  (rivit->summa lupausryhmat :merkitsevat-odottaa-kannanottoa))

(defn sallittu-kuukausi? [{:keys [kirjaus-kkt paatos-kk] :as lupaus} kuukausi paatos]
  {:pre [lupaus kuukausi (boolean? paatos)]}
  (let [sallittu? (if paatos
                    (or (= paatos-kk kuukausi)
                        ;; 0 = kaikki
                        (= paatos-kk 0))
                    (contains? (set kirjaus-kkt) kuukausi))]
    sallittu?))

(defn bonus-tai-sanktio
  "Bonuksia tulee kun toteutuneet pisteet ylittää lupauspisteet.
  Bonukset lasketaan kaavalla:
  0,0013 x (toteumapistemäärä - lupauspistemäärä) x tavoitehinta

  Sanktiota tulee kun toteutuneet pisteet alittaa lupauspisteet
  Sanktiot lasketaan kaavalla:
  0,0033 x (toteumapistemäärä - lupauspistemäärä) x tavoitehinta"
  [{:keys [toteuma lupaus tavoitehinta]}]
  (when (and (number? toteuma) (number? lupaus) (number? tavoitehinta) (pos? tavoitehinta))
    (cond
      (> toteuma lupaus)
      {:bonus (* 0.0013 (- toteuma lupaus) tavoitehinta)}
      (< toteuma lupaus)
      {:sanktio (* 0.0033 (- toteuma lupaus) tavoitehinta)}
      ;; Jos pisteet täsmää, niin tavoite on täytetty
      :else
      {:tavoite-taytetty true})))

(defn vastauskuukausi?
  "Voiko kuukaudelle ylipäänsä antaa vastausta, eli onko se joko päätös- tai kirjauskuukausi."
  [{:keys [paattava-kuukausi? kirjauskuukausi?] :as lupaus-kuukausi}]
  (or (true? paattava-kuukausi?) (true? kirjauskuukausi?)))

(defn kayttaja-saa-vastata?
  "Saako käyttäjä vastata annettuun kuukauteen.
  Tilaajan käyttäjä saa vastata sekä päättäviin että kirjauskuukausiin.
  Urakoitsijan käyttäjä saa vastata vain kirjauskuukausiin."
  [kayttaja lupaus-kuukausi]
  (and (vastauskuukausi? lupaus-kuukausi)
       (or (:kirjauskuukausi? lupaus-kuukausi)
           (roolit/tilaajan-kayttaja? kayttaja))))

(defn ennusteen-tila->saa-vastata? [ennusteen-tila]
  ;; Vastauksia ei saa enää muuttaa välikatselmuksen jälkeen.
  (not= ennusteen-tila :katselmoitu-toteuma))

(defn ryhmat->lupaukset [ryhmat]
  (->> ryhmat
       (map :lupaukset)
       flatten))

(defn etsi-lupaus [lupaustiedot id]
  (->> (:lupausryhmat lupaustiedot)
       ryhmat->lupaukset
       (filter #(= id (:lupaus-id %)))
       first))

(defn etsi-lupaus-kuukausi [kuukaudet kohdekuukausi]
  (first (filter #(= kohdekuukausi (:kuukausi %)) kuukaudet)))

(defn lupauspaatokset
  "Suodata lupaustyyppiset päätökset urakan päätöksistä."
  [urakan-paatokset]
  (->> urakan-paatokset
       (filter #(#{"lupausbonus" "lupaussanktio"} (:harja.domain.kulut.valikatselmus/tyyppi %)))))

(defn valikatselmus-tehty?
  "Palauttaa true, jos päätöksissä on lupaus-tyyppinen päätös."
  [urakan-paatokset]
  (->> (lupauspaatokset urakan-paatokset)
       first
       boolean))

(defn paatos->bonus-tai-sanktio
  [{tyyppi :harja.domain.kulut.valikatselmus/tyyppi
    tilaajan-maksu :harja.domain.kulut.valikatselmus/tilaajan-maksu
    urakoitsijan-maksu :harja.domain.kulut.valikatselmus/urakoitsijan-maksu}]
  (case tyyppi
    "lupausbonus" {:bonus tilaajan-maksu}
    "lupaussanktio" {:sanktio urakoitsijan-maksu}
    nil))

(defn urakan-paatokset->lupauspaatos [urakan-paatokset]
  (first (lupauspaatokset urakan-paatokset)))

(defn urakan-paatokset->bonus-tai-sanktio [urakan-paatokset]
  (->>
    (urakan-paatokset->lupauspaatos urakan-paatokset)
    paatos->bonus-tai-sanktio))

(defn kokoa-vastauspisteet [kayttaja pistekuukaudet urakka-id valittu-hoitokausi
                            valikatselmus-tehty-hoitokaudelle? nykyhetki]

  (let [;; set/difference on helpompi hallita, jos karsitana osa vastauksen tiedoista
        karsitut-kuukausipisteet (set (map #(dissoc % :id :pisteet) pistekuukaudet))
        [hk-alkupvm hk-loppupvm] valittu-hoitokausi
        vuosi (pvm/vuosi hk-alkupvm)
        kuluva-vuosi (pvm/vuosi nykyhetki)
        kuluva-kuukausi (pvm/kuukausi nykyhetki)
        ;;TODO: Tämä reduce on melkein samanlainent myös tuolla alla. Vois koittaa jotenkin yhdistää
        vaaditut-kuukaudet (into #{}
                                 (reduce (fn [vastaukset kk]
                                           (let [kaytettava-vuosi (if (> kk 9)
                                                                    vuosi
                                                                    (inc vuosi))]
                                             (conj vastaukset
                                                   {:urakka-id urakka-id
                                                    :kuukausi kk
                                                    :vuosi kaytettava-vuosi
                                                    ;; Syyskuu on aina lopullinen toteuma kuukausi, jonka aluevastaava täyttää
                                                    :tyyppi (if (= kk 9)
                                                              "toteuma"
                                                              "ennuste")})))
                                         []
                                         kaikki-kuukaudet))
        ero (set/difference vaaditut-kuukaudet karsitut-kuukausipisteet)
        lopulliset-pisteet (concat pistekuukaudet ero)
        ;; Lisää kaikille kuukausille vielä ui:n kannalta valmiiksi pääteltyjä asioita - Harkitse toteutusta - voisi tehdä paremmin
        tilaajan-kayttaja? (or
                             (roolit/jvh? kayttaja)
                             (roolit/tilaajan-kayttaja? kayttaja)
                             (roolit/roolissa? kayttaja roolit/ely-urakanvalvoja)
                             (roolit/rooli-urakassa? kayttaja roolit/ely-urakanvalvoja urakka-id)
                             false)
        lopulliset-pisteet (into #{}
                                 (reduce (fn [lista p]
                                           (let [kk (:kuukausi p)
                                                 kaytettava-vuosi (if (> kk 9)
                                                                    vuosi
                                                                    (inc vuosi))
                                                 kuluva-kuukausi? (and
                                                                    (= kaytettava-vuosi kuluva-vuosi)
                                                                    (= kk kuluva-kuukausi))
                                                 ;; Tulevaisuuteen ei voi vastata
                                                 ;; Välikatselmuksen jälkeen ei voi vastata
                                                 ;; Ja urakoitsija ei voi vastata syyskuun pisteisiin, se on tilaajan hommia
                                                 voi-vastata? (and (not valikatselmus-tehty-hoitokaudelle?)
                                                                   (pvm/sama-tai-jalkeen? nykyhetki (pvm/->pvm (str "01." kk "." kaytettava-vuosi)))
                                                                   ;; Kuluvalle kuukaudelle ei voi vastata, mutta edelliselle voi
                                                                   (not kuluva-kuukausi?)
                                                                   ;; Syyskuuhun voi vastata vain tilaaja.
                                                                   (if (= 9 kk)
                                                                     tilaajan-kayttaja?
                                                                     true))]
                                             (conj lista
                                                   (merge p
                                                          {:kuluva-kuukausi? kuluva-kuukausi?
                                                           :voi-vastata? voi-vastata?
                                                           :odottaa-vastausta? (and voi-vastata?
                                                                                    (not (and
                                                                                           (not (nil? (:pisteet p)))
                                                                                           (> (:pisteet p) -1))))}))))
                                         []
                                         lopulliset-pisteet))
        lopulliset-pisteet (sort-by (juxt :vuosi :kuukausi) lopulliset-pisteet)]
    lopulliset-pisteet))

(defn vuosi-19-20?
  "Onko vuosi 2019 tai 2020?"
  [vuosi]
  (boolean (#{2019 2020} vuosi)))

(defn urakka-19-20?
  "Onko urakan alkuvuosi 2019 tai 2020?
  Näille urakoille on lupauksissa eri logiikka kuin 2021 tai myöhemmin alkaneille urakoille."
  [urakka]
  (-> urakka :alkupvm pvm/vuosi vuosi-19-20?))

(defn odottaa-urakoitsijan-kannanottoa?
  "Odottaako 19/20 alkanut urakka urakoitsijan kannanottoa."
  [kuukausipisteet]
  (let [;; Ensimmäiset 11 kuukautta annetaan ennusteet (loka-elokuu)
        ennustepisteet (take 11 kuukausipisteet)
        ;; Syyskuussa annetaan varsinainen päätös.
        paattavat-pisteet (last kuukausipisteet)]
    (and
      ;; Jos päättävä vastaus on jo annettu, ei lähetetä muistutusta.
      (not (:pisteet paattavat-pisteet))
      ;; Jos mikä tahansa muu kuukausi odottaa kannanottoa, niin lähetetään muistutus.
      (->>
        ennustepisteet
        (filter :odottaa-vastausta?)
        first
        boolean))))
