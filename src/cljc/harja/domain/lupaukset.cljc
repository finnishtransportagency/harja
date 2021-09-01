(ns harja.domain.lupaukset
  (:require [harja.pvm :as pvm]
            [clojure.set :as set]
            [taoensso.timbre :as log]))

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
    ;; Ensimmäiset ennusteet annetaan Marraskuun alussa, kun tiedot on syötetty ensimmäiseltä  kuukaudelta.
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

(defn viimeisin-vastaus [vastaukset]
  (last (sort-by (fn [{:keys [vuosi kuukausi]}]
                   [vuosi kuukausi])
                 vastaukset)))

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

(defn vastattu? [{:keys [lupaus-vaihtoehto-id vastaus]}]
  (or (number? lupaus-vaihtoehto-id) (boolean? vastaus)))

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

(defn vertaa-kuluvaan-kuukauteen [kuukausi kuluva-kuukausi]
  (cond (= kuukausi kuluva-kuukausi) :kuluva-kuukausi
        (hoitokuukausi-ennen? kuukausi kuluva-kuukausi) :mennyt-kuukausi
        :else :tuleva-kuukausi))

(def kaikki-kuukaudet [10 11 12 1 2 3 4 5 6 7 8 9])

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
    (filter #(hoitokuukausi-ennen? % kuluva-kuukausi))
    set))

(defn puuttuvat-vastauskuukaudet [{:keys [lupaustyyppi joustovara-kkta kirjaus-kkt paatos-kk vastaukset] :as lupaus}
                                  kuluva-kuukausi]
  (let [vastaus-kkt (->> vastaukset
                         (filter vastattu?)
                         (map :kuukausi)
                         set)
        vaaditut-kkt (vaaditut-vastauskuukaudet lupaus kuluva-kuukausi)]
    (set/difference vaaditut-kkt vastaus-kkt)))

(defn odottaa-kannanottoa? [{:keys [lupaustyyppi joustovara-kkta kirjaus-kkt paatos-kk vastaukset] :as lupaus}
                            kuluva-kuukausi]
  (if (lupaus->toteuma lupaus)
    ;; Jos toteuma voidaan laskea, niin lupaukseen ei tarvitse enää ottaa kantaa.
    false

    ;; Katsotaan onko vaaditut vastaukset annettu
    (boolean (seq (puuttuvat-vastauskuukaudet lupaus kuluva-kuukausi)))))

(defn paattele-kohdevuosi [kuukausi hoitokauden-alkuvuosi]
  (if (>= kuukausi 10)
    hoitokauden-alkuvuosi
    (inc hoitokauden-alkuvuosi)))

(defn lupaus->kuukaudet
  "Palauttaa hoitovuoden 12 kuukautta muodossa:
  {:kuukausi 10,
   :odottaa-kannanottoa? false,
   :paattava-kuukausi? true,
   :nykyhetkeen-verrattuna :mennyt-kuukausi,
   :vastaus true}"
  [{:keys [paatos-kk vastaukset kirjaus-kkt] :as lupaus} kuluva-kuukausi hoitokauden-alkuvuosi]
  (let [kk->vastaus (into {}
                          (map (fn [vastaus] [(:kuukausi vastaus) vastaus]))
                          vastaukset)
        odottaa-kannanottoa? (odottaa-kannanottoa? lupaus kuluva-kuukausi)
        paatos-kkt (paatos-kk-joukko paatos-kk)
        kirjaus-kkt (set kirjaus-kkt)
        puuttuvat-kkt (when odottaa-kannanottoa?
                        (puuttuvat-vastauskuukaudet lupaus kuluva-kuukausi))]
    (for [kuukausi kaikki-kuukaudet]
      (let [vastaus (kk->vastaus kuukausi)]
        (merge
          {:kuukausi kuukausi
           :vuosi (paattele-kohdevuosi kuukausi hoitokauden-alkuvuosi)
           :odottaa-kannanottoa? (and odottaa-kannanottoa?
                                      (contains? puuttuvat-kkt kuukausi))
           :paattava-kuukausi? (contains? paatos-kkt kuukausi)
           :kirjauskuukausi? (contains? kirjaus-kkt kuukausi)
           :nykyhetkeen-verrattuna (vertaa-kuluvaan-kuukauteen kuukausi kuluva-kuukausi)}
          (when vastaus
            {:vastaus vastaus}))))))

(defn liita-lupaus-kuukaudet [lupaus nykyhetki hoitokauden-alkuvuosi]
  (assoc lupaus :lupaus-kuukaudet
                (lupaus->kuukaudet lupaus (pvm/kuukausi nykyhetki) hoitokauden-alkuvuosi)))

(defn liita-odottaa-kannanottoa [lupaus nykyhetki]
  (assoc lupaus :odottaa-kannanottoa? (odottaa-kannanottoa? lupaus (pvm/kuukausi nykyhetki))))

(defn rivit->odottaa-kannanottoa [rivit]
  (->> rivit
       (filter :odottaa-kannanottoa?)
       count))

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

(defn sallittu-kuukausi? [{:keys [kirjaus-kkt paatos-kk] :as lupaus} kuukausi paatos]
  {:pre [lupaus kuukausi (boolean? paatos)]}
  (let [sallittu? (if paatos
                    (or (= paatos-kk kuukausi)
                        ;; 0 = kaikki
                        (= paatos-kk 0))
                    (contains? (set kirjaus-kkt) kuukausi))]
    (log/debug "sallittu-kuukausi?" sallittu? "kirjaus-kkt" kirjaus-kkt "paatos-kk" paatos-kk "kuukausi" kuukausi "paatos" paatos)
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
    (if (>= toteuma lupaus)
      {:bonus (* 0.0013 (- toteuma lupaus) tavoitehinta)}
      {:sanktio (* 0.0033 (- toteuma lupaus) tavoitehinta)})))
