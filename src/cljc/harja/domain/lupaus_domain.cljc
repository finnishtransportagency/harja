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

(defn kustannusennuste->ennuste
  "Laskee kustannusennusteen ennusteen kuukausittaisten kustannusennusteiden perusteella.
   Jos kaikki kuukaudet eivät ole täytetty, palauttaa viimeisimmän annetun ennusteen.
   Jos dataa ei löydy, palauttaa 0."
  [{:keys [lupaus-kuukaudet]}]
  (let [;; Hae kaikki kuukaudet, joissa on kustannusennusteita
        kuukaudet-pisteilla (->> lupaus-kuukaudet
                              (filter #(get-in % [:kustannusennuste :pisteet]))
                              (sort-by :kuukausi))
        ;; Jos on annettu kustannusennusteita, käytä viimeisintä
        viimeisin-pisteet (when (seq kuukaudet-pisteilla)
                            (get-in (last kuukaudet-pisteilla) [:kustannusennuste :pisteet]))]
    ;; Palauta 0 jos ei ole dataa, muuten viimeisin pistemäärä
    (or viimeisin-pisteet 0)))

(defn kustannusennuste->toteuma
  "Laskee kustannusennusteen toteuman kun se on mahdollista.
   Toteuma = keskiarvo kaikista kustannusennustekuukausista joissa on pisteitä.
   Jos ei ole pisteitä ollenkaan, palauttaa 0.
   
   HUOM: Toteuma lasketaan vain kun kaikki ennustekuukaudet on ohitettu ajallisesti.
   Tämän tarkistuksen tulee tapahtua kutsuvassa koodissa hoitovuoden tilan perusteella."
  [{:keys [lupaus-kuukaudet hoitovuosi-paattynyt?]}] 
    (when hoitovuosi-paattynyt?
      ;; Hae kaikki kuukaudet joissa on kustannusennusteita ja pisteitä
      (let [kuukaudet-pisteilla (->> lupaus-kuukaudet
                                  (filter #(get-in % [:kustannusennuste :pisteet]))
                                  (map #(get-in % [:kustannusennuste :pisteet])))]
        (if (seq kuukaudet-pisteilla)
          ;; Laske keskiarvo vain niistä kuukausista joissa on pisteitä
          (let [keskiarvo (/ (reduce + kuukaudet-pisteilla) (count kuukaudet-pisteilla))]
            #?(:clj (Math/round (double keskiarvo))
               :cljs (js/Math.round keskiarvo)))
          ;; Jos ei ole pisteitä ollenkaan, palauta 0
          0))))

(defn lupaus->ennuste [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->ennuste lupaus)
    "kysely" (kysely->ennuste lupaus)
    "monivalinta" (monivalinta->ennuste lupaus)
    "kustannusennuste" (kustannusennuste->ennuste lupaus)))

(defn lupaus->toteuma [{:keys [lupaustyyppi] :as lupaus}]
  (case lupaustyyppi
    "yksittainen" (yksittainen->toteuma lupaus)
    "kysely" (monivalinta->toteuma lupaus)
    "monivalinta" (monivalinta->toteuma lupaus)
    "kustannusennuste" (kustannusennuste->toteuma lupaus)))

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
      ;; Suodata vain kuluvan kuukauden sisään
      (filter #(or
                 (nil? kuluva-kuukausi)
                 (hoitokuukausi-ennen? % kuluva-kuukausi)))
      set)))


(defn puuttuvat-vastauskuukaudet-hoitovuodelle
  [lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot]
  (let [vastaus-kkt (->> (:vastaukset lupaus)
                      (filter vastattu?)
                      (map :kuukausi)
                      set)
        vaaditut-kkt (vaaditut-vastauskuukaudet-hoitovuodelle
                       lupaus kuluva-kuukausi hoitovuosi-nro hoitovuoden-erikoisarvot)]
    (set/difference vaaditut-kkt vastaus-kkt)))

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
  [{tyyppi :tyyppi
    tilaajan-maksu :lupausbonus
    urakoitsijan-maksu :lupaussanktio}]
  (case tyyppi
    "bonus" {:bonus tilaajan-maksu}
    "sanktio" {:sanktio urakoitsijan-maksu}
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

(defn validoi-kustannusennuste-syotteet
  "Validoi että kaikki syötteet ovat valideja laskentaa varten"
  [{:keys [ennustettu-tavoitehinta toteutunut-tavoitehinta 
           ennustettu-kustannus toteutunut-kustannus
           hoitovuoden-alun-tavoitehinta] :as syotteet}]
  {:pre [syotteet]}
  (cond
    (not (every? number? [ennustettu-tavoitehinta toteutunut-tavoitehinta 
                          ennustettu-kustannus toteutunut-kustannus
                          hoitovuoden-alun-tavoitehinta]))
    {:virhe "Kaikki arvot on oltava numeroita"}

    (zero? hoitovuoden-alun-tavoitehinta)
    {:virhe "Hoitovuoden tavoitehinta ei voi olla nolla (nollajako)"}

    (neg? hoitovuoden-alun-tavoitehinta)
    {:virhe "Hoitovuoden tavoitehinta ei voi olla negatiivinen"}

    :else
    {:ok true}))

(defn turvallinen-jako
  "Turvallinen jakolasku joka välttää BigDecimal-ongelmat ja toimii sekä CLJ että CLJS:ssä"
  [a b]
  (if (zero? b)
    0.0
    #?(:clj (double (/ (double a) (double b)))
       :cljs (/ a b))))

(defn laske-kustannusennusteen-tarkkuus
  "Laskee kustannusennusteen tarkkuuden ja palauttaa tietokantaan tallennettavan muodon."
  [{:keys [ennustettu-tavoitehinta toteutunut-tavoitehinta
           ennustettu-kustannus toteutunut-kustannus
           hoitovuoden-alun-tavoitehinta] :as syotteet}]
  (let [validointi (validoi-kustannusennuste-syotteet syotteet)]
    (if (:virhe validointi)
      validointi
      (let [te (double ennustettu-tavoitehinta)
            tt (double toteutunut-tavoitehinta)
            ke (double ennustettu-kustannus)
            kt (double toteutunut-kustannus)
            th (double hoitovuoden-alun-tavoitehinta)

            ;; Laskentavaiheet
            tavoitehinta-ero (turvallinen-jako (- te tt) th)
            kustannus-ero (turvallinen-jako (- ke kt) th)
            riski-ero (turvallinen-jako (- (- ke te) (- kt tt)) th)

            ;; Lopputulos
            tarkkuus (+ (* tavoitehinta-ero 0.05)
                        (* kustannus-ero 0.05)
                        (* riski-ero 0.9))
            tarkkuus-prosentti (/ (Math/round (* tarkkuus 1000.0)) 10.0)

            ;; Tallennettava data
            kaava-versio "v1.0"
            kaava-teksti "x = [(Te - Tt)/Th × 0.05 + (Ke - Kt)/Th × 0.05 + [(Ke - Te) - (Kt - Tt)]/Th × 0.9]"

            parametrit {:Te te :Tt tt :Ke ke :Kt kt :Th th
                        :kertoimet {:tavoitehinta-kerroin 0.05
                                    :kustannus-kerroin 0.05
                                    :riski-kerroin 0.9}}

            vaiheet {:vaihe-1 {:kuvaus "Tavoitehinnan ero"
                               :kaava "(Te - Tt) / Th"
                               :laskenta (str "(" te " - " tt ") / " th)
                               :tulos tavoitehinta-ero}
                     :vaihe-2 {:kuvaus "Kustannuksen ero"
                               :kaava "(Ke - Kt) / Th"
                               :laskenta (str "(" ke " - " kt ") / " th)
                               :tulos kustannus-ero}
                     :vaihe-3 {:kuvaus "Riskin ero"
                               :kaava "[(Ke - Te) - (Kt - Tt)] / Th"
                               :laskenta (str "[(" ke " - " te ") - (" kt " - " tt ")] / " th)
                               :tulos riski-ero}
                     :vaihe-4 {:kuvaus "Lopputulos"
                               :kaava "vaihe-1 × 0.05 + vaihe-2 × 0.05 + vaihe-3 × 0.9"
                               :laskenta (str tavoitehinta-ero " × 0.05 + " kustannus-ero " × 0.05 + " riski-ero " × 0.9")
                               :tulos tarkkuus}
                     :lopputulos-prosentti tarkkuus-prosentti}]

        {:tarkkuus-prosentti tarkkuus-prosentti
         :laskentakaava-versio kaava-versio
         :laskentakaava-teksti kaava-teksti
         :laskentakaava-parametrit parametrit
         :laskentakaava-vaiheet vaiheet}))))

(defn maarita-kustannusennuste-pisteet-kovakoodattu
  "Määrittää pisteet tarkkuuden ja kuukauden perusteella.
   Kuukausikohtaiset raja-arvot määrittävät pistemäärän."
  [tarkkuus-prosentti kuukausi]
  {:pre [(number? tarkkuus-prosentti) (number? kuukausi)]}
  (let [tarkkuus-itseisarvo (Math/abs tarkkuus-prosentti)]
    (case kuukausi
      ;; Lokakuu: ≤ 7,0% = 8p, ≤ 9,0% = 4p, > 9,0% = 1p
      10 (cond
           (<= tarkkuus-itseisarvo 7.0) 8
           (<= tarkkuus-itseisarvo 9.0) 4
           :else 1)

      ;; Tammikuu: ≤ 4,0% = 8p, ≤ 6,0% = 4p, > 6,0% = 1p
      1 (cond
          (<= tarkkuus-itseisarvo 4.0) 8
          (<= tarkkuus-itseisarvo 6.0) 4
          :else 1)

      ;; Huhtikuu: ≤ 2,0% = 8p, ≤ 3,0% = 4p, > 3,0% = 1p
      4 (cond
          (<= tarkkuus-itseisarvo 2.0) 8
          (<= tarkkuus-itseisarvo 3.0) 4
          :else 1)

      ;; Kesäkuu: ≤ 1,0% = 8p, ≤ 2,0% = 4p, > 2,0% = 1p
      6 (cond
          (<= tarkkuus-itseisarvo 1.0) 8
          (<= tarkkuus-itseisarvo 2.0) 4
          :else 1)

      ;; Elokuu: samat arvot kuin lokakuu
      8 (cond
          (<= tarkkuus-itseisarvo 7.0) 8
          (<= tarkkuus-itseisarvo 9.0) 4
          :else 1)

      ;; Muut kuukaudet - oletusarvo
      0)))

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
