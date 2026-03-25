(ns harja.domain.lupaus-domain
  (:require
   [clojure.set :as set]
   [harja.domain.oikeudet :as oikeudet]
   [harja.domain.roolit :as roolit]
   [harja.domain.lupaus.kustannusennuste-domain :as kustannusennuste-domain]
   [harja.pvm :as pvm]))

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
    ;; Ensimmäiset ennusteet annetaan lokakuun alussa, kun pidetty ensimmäiseltä kuukaudelta.
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

(defn kustannusennuste? [lupaus]
  (kustannusennuste-domain/kustannusennuste? lupaus))

(defn hylatyt [vastaukset]
  (filter #(false? (:vastaus %)) vastaukset))

(defn hyvaksytyt [vastaukset]
  (filter #(true? (:vastaus %)) vastaukset))

(defn paatokset [vastaukset]
  (filter :paatos vastaukset))

(defn hylatty? [vastaukset joustovara-kkta]
  (> (count (hylatyt vastaukset)) joustovara-kkta))

(defn hyvaksytty? [vastaukset joustovara-kkta paatos-kk]
  (let [vastaus-kuukaudet-lkm (if (= (first paatos-kk) 0)
                                12                          ; 0 = kaikki
                                (count paatos-kk))
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
    "monivalinta" (monivalinta->ennuste lupaus)
    "kustannusennuste" (kustannusennuste-domain/kustannusennuste->ennuste lupaus)))

(defn lupaus->toteuma [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->toteuma lupaus)
    "kysely" (monivalinta->toteuma lupaus)
    "monivalinta" (monivalinta->toteuma lupaus)
    "kustannusennuste" (kustannusennuste-domain/kustannusennuste->toteuma lupaus)))

(defn lupaus->maksimipisteet
  "Palauttaa lupauksen maksimipisteet lupaustyypistä riippumatta.
   Backend palauttaa :kyselypisteet arvoksi vähintään 0 jos muuta arvoa ei ole."
  [{:keys [lupaustyyppi pisteet kyselypisteet]}]
  (case lupaustyyppi
    "yksittainen" pisteet
    "kustannusennuste" pisteet
    ; kysely ja monivalinta käyttävät kyselypisteitä, defaulttaa 0:aan
    (or kyselypisteet 0)))

(defn lupaus->pistenakyma
  "Palauttaa lupauksen pistenäytön merkkijonona UI:ta varten.
   Yksittäinen: näyttää vain pisteet
   Kustannusennuste ja muut: näyttää 'Pisteet 0 - X'"
  [{:keys [lupaustyyppi pisteet kyselypisteet] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (str pisteet)
    "kustannusennuste" (str "Pisteet 0 - " pisteet)
    ; kysely ja monivalinta
    (str "Pisteet 0 - " (or kyselypisteet 0))))

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

#_ (defn paatos-kk-joukko [paatos-kk]
  (if (= 0 paatos-kk)
    (set kaikki-kuukaudet)
    #{paatos-kk}))

(defn paatos-kk-joukko
  "Päätös-kk on uudemmassa versiossa vectori, esim [0] tai [4,9] tai [12]. 0 = kaikki kuukaudet.
  Aiemmin päätöskk on ollut yksittäinen numero."
  [paatos-kk]
  (if (= 0 (first paatos-kk))
    (set kaikki-kuukaudet)
    (set paatos-kk)))

(defn hoitovuoden-kirjauskuukaudet [lupaus _hoitovuosi-nro hoitovuoden-erikoisarvot]
  (or (:kirjaus-kkt hoitovuoden-erikoisarvot)
    (:kirjaus-kkt lupaus)))

(defn hoitovuoden-paatos-kk [lupaus _hoitovuosi-nro hoitovuoden-erikoisarvot]
  (or (:paatos-kk hoitovuoden-erikoisarvot)
    (:paatos-kk lupaus)))

(defn hoitovuoden-joustovara [lupaus _hoitovuosi-nro hoitovuoden-erikoisarvot]
  (or (:joustovara-kkta hoitovuoden-erikoisarvot)
    (:joustovara-kkta lupaus)))

(defn vaaditut-vastauskuukaudet-hoitovuodelle
  [lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot]
  (let [kaytettavat-kirjaus-kkt (hoitovuoden-kirjauskuukaudet lupaus hoitovuosi-nro hoitovuoden-erikoisarvot)
        kaytettava-paatos-kk (hoitovuoden-paatos-kk lupaus hoitovuosi-nro hoitovuoden-erikoisarvot)]
    (->>
      ;; Yhdistä kirjaus- ja päätöskuukaudet
      (set/union (set kaytettavat-kirjaus-kkt)
        (paatos-kk-joukko kaytettava-paatos-kk))
      ;; Kustannusennusteelle eri logiikka kuin muille
      (filter #(or
                 (nil? kuluva-kuukausi)
                 (if (= "kustannusennuste" (:lupaustyyppi lupaus))
                   ;; Kustannusennusteelle: näytä vain kuluva kuukausi
                   (= % kuluva-kuukausi)
                   ;; Muille: näytä kuluvan kuukauden edeltävät kuukaudet
                   (hoitokuukausi-ennen? % kuluva-kuukausi))))
      set)))


(defn puuttuvat-vastauskuukaudet-hoitovuodelle
  [lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot]
  (let [;; Tavallisten vastausten kuukaudet
        vastaus-kkt (->> (:vastaukset lupaus)
                      (filter vastattu?)
                      (map :kuukausi)
                      set)

        ;; Kustannusennusteiden kuukaudet (jos lupaus on kustannusennuste)
        kustannusennuste-kkt (if (= "kustannusennuste" (:lupaustyyppi lupaus))
                               (->> (:kustannusennusteet lupaus)
                                 (filter #(and (:tavoitehinta %) (:toteutuneet-kustannukset %)))
                                 (map #(pvm/kuukausi (:maarapaiva %)))
                                 set)
                               #{})

        ;; Yhdistetään kaikki vastatut kuukaudet
        kaikki-vastatut-kkt (set/union vastaus-kkt kustannusennuste-kkt)
        vaaditut-kkt (vaaditut-vastauskuukaudet-hoitovuodelle
                       lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot)]
    (set/difference vaaditut-kkt kaikki-vastatut-kkt)))

;; Päivitä vanha funktio käyttämään uutta:
(defn puuttuvat-vastauskuukaudet [{:keys [lupaustyyppi joustovara-kkta kirjaus-kkt paatos-kk vastaukset] :as lupaus}
                                  kuluva-kuukausi]
  (puuttuvat-vastauskuukaudet-hoitovuodelle lupaus kuluva-kuukausi nil nil))

(defn maarita-kuluva-kuukausi-hoitokaudelle
  "Määrittää mikä kuluva-kuukausi parametri pitää antaa 
   odottaa-kannanottoa-kkt-hoitovuodelle funktiolle hoitokauden 
   aikatilan perusteella."
  [nykyhetki valittu-hoitokausi]
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi]
    (cond
      ;; Tuleviin hoitokausiin ei oteta kantaa
      (pvm/ennen? nykyhetki hk-alkupvm)
      :tuleva-hoitokausi

      ;; Menneet hoitokaudet: ei määritetä kuluvaa kuukautta
      (pvm/jalkeen? nykyhetki hk-loppupvm)
      nil

      ;; Kuluva hoitokausi lasketaan kuluvan kuukauden perusteella
      :else
      (pvm/kuukausi nykyhetki))))

(defn odottaa-kannanottoa-kkt-hoitovuodelle
  [lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot]
  (cond
    ;; Tuleviin hoitokausiin ei oteta kantaa
    (= kuluva-kuukausi :tuleva-hoitokausi)
    []

    ;; Jos toteuma voidaan laskea, ei tarvitse ottaa kantaa
    (lupaus->toteuma lupaus)
    []

    ;; Muuten laske puuttuvat kuukaudet
    :else
    (puuttuvat-vastauskuukaudet-hoitovuodelle
      lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot)))

(defn odottaa-kannanottoa-kkt-hoitokaudelle
  "Laskee puuttuvat kannanottokuukaudet ottaen huomioon hoitokauden aikatilan."
  [lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot]
  (let [kuluva-kuukausi (maarita-kuluva-kuukausi-hoitokaudelle nykyhetki valittu-hoitokausi)]
    (odottaa-kannanottoa-kkt-hoitovuodelle lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot)))

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
   :vastaus true,
   :kustannusennuste {...}}"
  [{:keys [vastaukset kustannusennusteet lupaustyyppi] :as lupaus}
   nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot maarapaiva-tiedot]
  (let [[hk-alkupvm hk-loppupvm] valittu-hoitokausi
        kuluva-vuosi (pvm/vuosi nykyhetki)
        kuluva-kuukausi (pvm/kuukausi nykyhetki)
        kk->vastaus (into {}
                          (map (fn [vastaus] [(:kuukausi vastaus) vastaus]))
                          vastaukset)
        ;; Lisätään kustannusennusteiden map kuukauden mukaan
        kk->kustannusennuste (when kustannusennusteet
                               (into {}
                                     (map (fn [ke] [(pvm/kuukausi (:maarapaiva ke)) ke]))
                                     kustannusennusteet)) 
        kaytettavat-kirjaus-kkt (set (hoitovuoden-kirjauskuukaudet lupaus hoitovuosi-nro hoitovuoden-erikoisarvot))
        kaytettava-paatos-kk (hoitovuoden-paatos-kk lupaus hoitovuosi-nro hoitovuoden-erikoisarvot)
        puuttuvat-kkt (odottaa-kannanottoa-kkt-hoitokaudelle lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot)
        kaytettava-joustovara (hoitovuoden-joustovara lupaus hoitovuosi-nro hoitovuoden-erikoisarvot)
        paatos-kkt (paatos-kk-joukko kaytettava-paatos-kk)
        kirjaus-kkt kaytettavat-kirjaus-kkt
        paatos-hylatty? (paatos-hylatty? vastaukset kaytettava-joustovara)
         ;; Kustannusennustelupaukselle karsitaan määräpäivätiedot vain kirjauskuukausille
        karsitut-maarapaiva-tiedot (when (and (= "kustannusennuste" lupaustyyppi) maarapaiva-tiedot)
                                     (select-keys maarapaiva-tiedot kaytettavat-kirjaus-kkt))
        ;; Käytetään karsittuja määräpäivätietoja kustannusennusteille, muille alkuperäisiä
        kaytettavat-maarapaiva-tiedot (if (= "kustannusennuste" lupaustyyppi)
                                        karsitut-maarapaiva-tiedot
                                        maarapaiva-tiedot)]
    (for [{:keys [vuosi kuukausi]} (hoitokuukaudet (pvm/vuosi hk-alkupvm))]
      (let [vastaus (kk->vastaus kuukausi)
            kustannusennuste (when kk->kustannusennuste
                               (kk->kustannusennuste kuukausi))
            maarapaiva-tieto (when kaytettavat-maarapaiva-tiedot 
                               (get kaytettavat-maarapaiva-tiedot kuukausi))
            syotetty-ajoissa? (when (and kustannusennuste maarapaiva-tieto)
                                (pvm/ennen? (:syotetty_pvm kustannusennuste) 
                                  (:maarapaiva-pvm maarapaiva-tieto)))]
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
            {:vastaus vastaus})
          (when kustannusennuste
            {:kustannusennuste kustannusennuste})
          (when maarapaiva-tieto
            {:maarapaiva-mennyt-ohi? (:maarapaiva-mennyt-ohi? maarapaiva-tieto)
             :maarapaiva-pvm (:maarapaiva-pvm maarapaiva-tieto)
             :syotetty-ajoissa? syotetty-ajoissa?}))))))

(defn liita-lupaus-kuukaudet
  ([lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot]
   ;; Perus signature ilman kustannusennusteita (nil-käsittely)
   (liita-lupaus-kuukaudet lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot nil nil))
  ([lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot kustannusennusteet]
   ;; Signature kustannusennusteilla mutta ilman määräpäivätietoja
   (liita-lupaus-kuukaudet lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot kustannusennusteet nil))
  ([lupaus nykyhetki valittu-hoitokausi hoitovuosi-nro hoitovuoden-erikoisarvot kustannusennusteet maarapaiva-tiedot]
   ;; Täysi signature kustannusennusteilla ja määräpäivätiedoilla
   (let [lupaus-kustannusennusteilla (if kustannusennusteet
                                       (assoc lupaus :kustannusennusteet kustannusennusteet)
                                       lupaus)]
     (assoc lupaus-kustannusennusteilla :lupaus-kuukaudet
       (lupaus->kuukaudet lupaus-kustannusennusteilla nykyhetki valittu-hoitokausi
         hoitovuosi-nro hoitovuoden-erikoisarvot maarapaiva-tiedot)))))

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


(defn sallittu-kuukausi-hoitovuodelle? [lupaus kuukausi paatos hoitovuosi-nro hoitovuoden-erikoisarvot]
  {:pre [lupaus kuukausi (boolean? paatos)]}
  (let [kirjaus-kkt (hoitovuoden-kirjauskuukaudet lupaus hoitovuosi-nro hoitovuoden-erikoisarvot)
        paatos-kk (hoitovuoden-paatos-kk lupaus hoitovuosi-nro hoitovuoden-erikoisarvot)]
    (if paatos
      (or (boolean (some #(= kuukausi %) paatos-kk))
        (boolean (some #(= 0 %) paatos-kk)))
      (boolean (some #(= kuukausi %) kirjaus-kkt)))))

;; Taaksepäinyhteensopivuus: jos ei annettu hoitovuositietoja, käytetään perusarvoja
(defn sallittu-kuukausi? [lupaus kuukausi paatos]
  (sallittu-kuukausi-hoitovuodelle? lupaus kuukausi paatos nil nil))

(defn bonus-tai-sanktio-19-20-urakalle
  "Bonuksia tulee kun toteutuneet pisteet ylittää lupauspisteet.
  Bonukset lasketaan kaavalla:
  0,0013 x (toteumapistemäärä - lupauspistemäärä) x tavoitehinta

  Sanktiota tulee kun toteutuneet pisteet alittaa lupauspisteet
  Sanktiot lasketaan kaavalla:
  0,0033 x (lupauspistemäärä - toteumapistemäärä) x tavoitehinta
  
  HUOM: Tämä funktio käyttää kiinteitä kertoimia ja on jätetty 2019/2020-urakoiden vanhan polun yhteensopivuuden vuoksi.
  Uusi yhteinen laskenta on funktiossa laske-lupauspaatos-bonus-tai-sanktio.
  
  Palauttaa:
  - {:bonus <positiivinen>} kun bonus
  - {:sanktio <negatiivinen>} kun legacy-polku tarvitsee sanktion indeksikorjaukseen
  - {:tavoite-taytetty true} kun tavoite täytetty"
  [{:keys [toteuma lupaus tavoitehinta]}]
  (when (and (number? toteuma) (number? lupaus) (number? tavoitehinta) (pos? tavoitehinta))
    (cond
      (> toteuma lupaus)
      {:bonus (* 0.0013 (- toteuma lupaus) tavoitehinta)}
      (< toteuma lupaus)
      {:sanktio (* -0.0033 (- lupaus toteuma) tavoitehinta)}
      ;; Jos pisteet täsmää, niin tavoite on täytetty
      :else
      {:tavoite-taytetty true})))

(defn laske-lupauspaatos-bonus-tai-sanktio
  "Yhteinen lupausbonus/sanktio-laskenta, joka käyttää välikatselmuksen logiikkaa.
  
  Bonuksia tulee kun toteutuneet pisteet ylittää lupauspisteet:
    bonus = (bonusprosentti / 100) × tavoitehinta × (toteutuneet - luvatut)
  
  Sanktiota tulee kun toteutuneet pisteet alittaa lupauspisteet:
    sanktio = (sanktioprosentti / 100) × tavoitehinta × (luvatut - toteutuneet)
  
  Parametrit:
  - toteutuneet-pisteet: Toteutuneet lupauksen pisteet
  - luvatut-pisteet: Luvatut lupauksen pisteet
  - tavoitehinta: Urakan tavoitehinta (tarjouksen tavoitehinta)
  - sanktioprosentti: Sanktioprosentti urakan parametreista (esim. 0.33 tai 0.18)
  - bonusprosentti: Bonusprosentti urakan parametreista (esim. 0.13)
  
  Palauttaa:
  - {:lupausbonus <positiivinen luku>} kun toteutuneet > luvatut
  - {:lupaussanktio <positiivinen luku>} kun toteutuneet < luvatut
  - {:tavoite-taytetty true} kun toteutuneet == luvatut
  - nil jos parametrit puuttuvat tai tavoitehinta <= 0"
  [{:keys [toteutuneet-pisteet luvatut-pisteet tavoitehinta sanktioprosentti bonusprosentti]}]
  (when (and (number? toteutuneet-pisteet)
             (number? luvatut-pisteet)
             (number? tavoitehinta)
             (number? sanktioprosentti)
             (number? bonusprosentti)
             (pos? tavoitehinta))
    (let [erotus (- luvatut-pisteet toteutuneet-pisteet)]
      (cond
        ;; Toteutuneet pisteet ylittää luvatut -> bonus
        (< erotus 0)
        {:lupausbonus (* (/ bonusprosentti 100) tavoitehinta (Math/abs ^long erotus))}
        
        ;; Toteutuneet pisteet alittaa luvatut -> sanktio (positiivinen)
        (> erotus 0)
        {:lupaussanktio (* (/ sanktioprosentti 100) tavoitehinta erotus)}
        
        ;; Pisteet täsmäävät
        :else
        {:tavoite-taytetty true}))))

(defn vastauskuukausi?
  "Voiko kuukaudelle ylipäänsä antaa vastausta, eli onko se joko päätös- tai kirjauskuukausi."
  [{:keys [paattava-kuukausi? kirjauskuukausi?] :as lupaus-kuukausi}]
  (or (true? paattava-kuukausi?) (true? kirjauskuukausi?)))

(defn kayttaja-saa-vastata?
  "Saako käyttäjä vastata annettuun kuukauteen.
  
  Excel (roolit.xlsx) määrittää kaikki oikeudet - katso dokumentaatio excelistä tai oikeudet.cljc"
  [kayttaja lupaus-kuukausi lupaustyyppi urakka-id]
  (and (vastauskuukausi? lupaus-kuukausi)
    (boolean
      ;; Ota huomioon että lupaus-kuukausi voi olla sekä päättävä-kuukausi? että kirjauskuukausi?
      (cond
        ;; Päättävät kuukaudet: Excel määrittää erikoisoikeudet
        (:paattava-kuukausi? lupaus-kuukausi)
        (case lupaustyyppi
          ;; Kustannusennuste: Excel määrittää kuka saa (W,kustannusennuste)
          "kustannusennuste"
          (oikeudet/on-muu-oikeus? "kustannusennuste"
                                   oikeudet/urakat-lupaukset
                                   urakka-id
                                   kayttaja)
          
          ;; Muut lupaukset: Excel määrittää kuka saa tehdä päätöksiä (W,päätös)
          (oikeudet/on-muu-oikeus? "päätös"
                                   oikeudet/urakat-lupaukset
                                   urakka-id
                                   kayttaja))
        
        ;; Kirjauskuukaudet: perustason kirjoitusoikeus riittää (Excel: W)
        (:kirjauskuukausi? lupaus-kuukausi)
        (oikeudet/voi-kirjoittaa? oikeudet/urakat-lupaukset urakka-id kayttaja)
        
        ;; Ei kirjaus- eikä päättävä kuukausi
        :else false))))

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

(defn etsi-nykyinen-valinta-askel [valittu-arvo vaihtoehdot]
  (let [arvo (first (filter #(= (:id %) valittu-arvo) vaihtoehdot))
        vaihtoehto-askel (:vaihtoehto-askel arvo)]
    vaihtoehto-askel))

(defn etsi-edeltavat-monivalinnan-valitut-arvot [valittu-arvo vaihtoehdot]
  (let [arvo (first (filter #(= (:id %) valittu-arvo) vaihtoehdot))
        askel (:vaihtoehto-askel arvo)
        edellinen-valinta (when askel
                            (first (filter #(= (:vaihtoehto-seuraava-ryhma-id %) askel) vaihtoehdot)))]
    (if edellinen-valinta
      (cons edellinen-valinta (etsi-edeltavat-monivalinnan-valitut-arvot (:id edellinen-valinta) vaihtoehdot))
      [])))

(defn paatos->bonus-tai-sanktio
  "Muuntaa tietokannasta haetun päätöksen API-muotoon.
  
  Tietokanta sisältää:
  - :lupausbonus (positiivinen) ja :tyyppi 'bonus'
  - :lupaussanktio (positiivinen) ja :tyyppi 'sanktio'
  - :tyyppi 'taytetty' kun tavoite on täytetty
  
  API-muoto:
  - {:bonus <positiivinen>}
  - {:sanktio <positiivinen>}
  - {:tavoite-taytetty true}"
  [{tyyppi :tyyppi
    tilaajan-maksu :lupausbonus
    urakoitsijan-maksu :lupaussanktio}]
  (case tyyppi
    "bonus" {:bonus tilaajan-maksu}
    "sanktio" {:sanktio urakoitsijan-maksu}
    "taytetty" {:tavoite-taytetty true}
    nil))

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


