(ns harja.domain.tierekisteri
  "Apufunktioita tierekisteriosoitteiden (TIE / AOSA / AET / LOSA LET) käsittelyyn."
  (:require [clojure.spec.alpha :as s]
            [clojure.set :as clj-set]
            [harja.tyokalut.spec-apurit :as spec-apurit]
            [clojure.string :as str]
    #?@(:clj [
            ])
            [harja.math :as math]
            [harja.geo :as geo]))

;; Osan tiedot
(s/def ::osa (s/and pos-int? #(< % 1000)))
(s/def ::etaisyys (s/and nat-int? #(< % 50000)))

;; Tien tiedot
(s/def ::numero (s/and pos-int? #(< % 100000)))
(s/def ::alkuosa ::osa)
(s/def ::alkuetaisyys ::etaisyys)
(s/def ::loppuosa ::osa)
(s/def ::loppuetaisyys ::etaisyys)

;; Yleiset suureet
(s/def ::pituus (s/and int? #(s/int-in-range? 1 spec-apurit/postgres-int-max %)))

;; Halutaan tierekisteriosoite, joka voi olla pistemäinen tai sisältää myös
;; loppuosan ja loppuetäisyyden.
;; Tämä voitaisiin s/or tehdä ja tarkistaa että pistemäisessä EI ole loppuosa/-etäisyys
;; kenttiä, mutta se johtaa epäselviin validointiongelmiin.
(s/def ::tierekisteriosoite
  (s/keys :req-un [::numero
                   ::alkuosa ::alkuetaisyys]
          :opt-un [::loppuosa ::loppuetaisyys]))


(defn muunna-osoitteen-avaimet [tie-avain
                                alkuosa-avain
                                alkuetaisyys-avain
                                loppuosa-avain
                                loppuetaisyys-avain
                                ajorata-avain
                                kaista-avain
                                osoite]
  (let [osoite (or osoite {})
        ks (fn [& avaimet]
             (some osoite avaimet))]
    {tie-avain (ks ::tie :numero :tr-numero :tr_numero :tie)
     alkuosa-avain (ks ::aosa :alkuosa :tr-alkuosa :tr_alkuosa :aosa)
     alkuetaisyys-avain (ks ::aet :alkuetaisyys :tr-alkuetaisyys :tr_alkuetaisyys :aet)
     loppuosa-avain (ks ::losa :loppuosa :tr-loppuosa :tr_loppuosa :losa)
     loppuetaisyys-avain (ks ::let :loppuetaisyys :tr-loppuetaisyys :tr_loppuetaisyys :let)
     ajorata-avain (ks ::arj ::ajorata :ajr :ajorata :tr-ajorata :tr_ajorata)
     kaista-avain (ks ::kaista :kaista :tr-kaista :tr_kaista)}))

(defn normalisoi
  "Muuntaa ei-ns avaimet :harja.domain.tierekisteri avaimiksi."
  [osoite]
  (muunna-osoitteen-avaimet ::tie ::aosa ::aet ::losa ::let ::ajorata :kaista osoite))

(defn tr-alkuiseksi [osoite]
  "Muuntaa osoitteen avaimet tr-prefiksatuiksi"
  (muunna-osoitteen-avaimet :tr-tie :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys :tr-ajorata :tr-kaista osoite))

(defn samalla-tiella? [tie1 tie2]
  (= (::tie (normalisoi tie1)) (::tie (normalisoi tie2))))

(defn sama-tr-osoite? [tr1 tr2]
  (= (normalisoi tr1) (normalisoi tr2)))

(defn ennen?
  "Tarkistaa alkaako tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teilla, palauttaa nil."
  [tie1 tie2]
  (let [tie1 (normalisoi tie1)
        tie2 (normalisoi tie2)])
  (when (samalla-tiella? tie1 tie2)
    (or (< (::aosa tie1) (::aosa tie2))
        (and (= (::aosa tie1) (::aosa tie2))
             (< (::aet tie1) (::aet tie2))))))

(defn alku
  "Palauttaa annetun tien alkuosan ja alkuetäisyyden vektorina"
  [{:keys [tr-alkuosa tr-alkuetaisyys]}]
  [tr-alkuosa tr-alkuetaisyys])

(defn loppu
  "Palauttaa annetun tien loppuosan ja loppuetäisyyden vektorina"
  [{:keys [tr-loppuosa tr-loppuetaisyys]}]
  [tr-loppuosa tr-loppuetaisyys])

(defn jalkeen?
  "Tarkistaa loppuuko tie1 osa ennen tie2 osaa. Osien tulee olla samalla tienumerolla.
  Jos osat ovat eri teillä, palauttaa nil."
  [tie1 tie2]
  (when (samalla-tiella? tie1 tie2)
    (or (> (:tr-loppuosa tie1) (:tr-loppuosa tie2))
        (and (= (:tr-loppuosa tie1) (:tr-loppuosa tie2))
             (> (:tr-loppuetaisyys tie1) (:tr-loppuetaisyys tie2))))))

(defn nouseva-jarjestys
  "Tarkistaa, että annettu osoite on nousevassa järjestyksessä (alku ennen loppua) ja
  kääntää alkuosan ja loppuosan, jos ei ole. Palauttaa mahdollisesti muokatun osoitteen."
  [{:keys [tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as osoite}]
  (cond
    (< tr-loppuosa tr-alkuosa)
    (assoc osoite
      :tr-alkuosa tr-loppuosa
      :tr-alkuetaisyys tr-loppuetaisyys
      :tr-loppuosa tr-alkuosa
      :tr-loppuetaisyys tr-alkuetaisyys)

    (and (= tr-loppuosa tr-alkuosa)
         (< tr-loppuetaisyys tr-alkuetaisyys))
    (assoc osoite
      :tr-loppuetaisyys tr-alkuetaisyys
      :tr-alkuetaisyys tr-loppuetaisyys)

    :default
    osoite))

(defn on-alku? [tie]
  (let [tie (normalisoi tie)]
    (and (integer? (::aosa tie))
         (integer? (::aet tie)))))

(defn on-loppu? [tie]
  (let [tie (normalisoi tie)]
    (and (integer? (::losa tie))
         (integer? (::let tie)))))

(defn on-alku-ja-loppu? [tie]
  (and (on-alku? tie)
       (on-loppu? tie)))

(defn laske-tien-pituus
  ([tie] (laske-tien-pituus {} tie))
  ([osien-pituudet {:keys [tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as tie}]
   (when (and (on-alku-ja-loppu? tie)
              (or (= tr-alkuosa tr-loppuosa)                ;; Pituus voidaan laskean suoraan
                  (not (empty? osien-pituudet))))           ;; Tarvitaan osien pituudet laskuun
     (let [{aosa :tr-alkuosa
            alkuet :tr-alkuetaisyys
            losa :tr-loppuosa
            loppuet :tr-loppuetaisyys} (nouseva-jarjestys tie)]
       (if (= aosa losa)
         (Math/abs (- loppuet alkuet))
         (let [max-osa (reduce max 0 (keys osien-pituudet))
               losa (min losa max-osa)]
           (loop [pituus (- (get osien-pituudet aosa 0) alkuet)
                  osa (inc aosa)]
             (let [osan-pituus (get osien-pituudet osa 0)]
               (if (>= osa losa)
                 (+ pituus (min loppuet osan-pituus))

                 (recur (+ pituus osan-pituus)
                        (inc osa)))))))))))

(defn validi-osoite? [osoite]
  (let [osoite (normalisoi osoite)]
    (and (some? (::tie osoite))
         (some? (::aosa osoite))
         (some? (::aet osoite)))))

(defn osa-olemassa-verkolla?
  "Tarkistaa, onko annettu osa olemassa Harjan tieverkolla (true / false)"
  [osa osien-pituudet]
  (number? (get osien-pituudet osa)))

(defn osan-pituus-sopiva-verkolla? [osa etaisyys ajoratojen-pituudet]
  "Tarkistaa, onko annettu osa sekä sen alku-/loppuetäisyys sopiva Harjan tieverkolla (true / false)"
  (if-let [hae (fn [ajorata] (:pituus
                               (first (filter #(and (= osa (:osa %))
                                                    (= ajorata (:ajorata %)))
                                              ajoratojen-pituudet))))]
    (let [nolla-ajoradan-pituus (hae 0)
          ykkosajoradan-pituus (hae 1)
          kakkosajoradan-pituus (hae 2)
          ajoradan-pituus (+ (or nolla-ajoradan-pituus 0)
                             (max (or ykkosajoradan-pituus 0)
                                  (or kakkosajoradan-pituus 0)))]
      (and (<= etaisyys ajoradan-pituus)
           (>= etaisyys 0)))
    false))

(defn tierekisteriosoite-tekstina
  "Näyttää tierekisteriosoitteen muodossa tie / aosa / aet / losa / let
   Vähintään tie, aosa ja aet tulee löytyä osoitteesta, jotta se näytetään

   Optiot on mappi, jossa voi olla arvot:
   teksti-ei-tr-osoitetta?        Näyttää tekstin jos TR-osoite puuttuu. Oletus true.
   teksti-tie?                    Näyttää sanan 'Tie' osoitteen edessä. Oletus true."
  ([tr] (tierekisteriosoite-tekstina tr {}))
  ([tr optiot]
   (let [tie-sana (let [sana "Tie "]
                    (if (nil? (:teksti-tie? optiot))
                      sana
                      (when (:teksti-tie? optiot) sana)))
         tie (or (:numero tr) (:tienumero tr) (:tr-numero tr) (:tie tr) (::tie tr))
         alkuosa (or (:alkuosa tr) (:tr-alkuosa tr) (:aosa tr) (::aosa tr))
         alkuetaisyys (or (:alkuetaisyys tr) (:tr-alkuetaisyys tr) (:aet tr) (::aet tr))
         loppuosa (or (:loppuosa tr) (:tr-loppuosa tr) (:losa tr) (::losa tr))
         loppuetaisyys (or (:loppuetaisyys tr) (:tr-loppuetaisyys tr) (:let tr) (::let tr))
         ei-tierekisteriosoitetta (if (or (nil? (:teksti-ei-tr-osoitetta? optiot))
                                          (boolean (:teksti-ei-tr-osoitetta? optiot)))
                                    "Ei tierekisteriosoitetta"
                                    "")]
     ;; Muodosta teksti
     (str (if tie
            (str tie-sana
                 tie
                 (when (and alkuosa alkuetaisyys)
                   (str " / " alkuosa " / " alkuetaisyys))
                 (when (and alkuosa alkuetaisyys loppuosa loppuetaisyys)
                   (str " / " loppuosa " / " loppuetaisyys)))
            ei-tierekisteriosoitetta)))))

(defn tieosoitteen-jarjestys
  "Palauttaa vectorin TR-osoitteen tiedoista. Voidaan käyttää järjestämään tieosoitteet järjestykseen."
  [kohde]
  ((juxt :tie :tr-numero :tienumero
         :ajr :tr-ajorata :ajorata
         :kaista :tr-kaista
         :aosa :tr-alkuosa
         :aet :tr-alkuetaisyys) kohde))

(defn jarjesta-tiet
  "Järjestää kohteet tieosoitteiden mukaiseen järjestykseen"
  [tiet]
  (sort-by tieosoitteen-jarjestys tiet))

(defn jarjesta-kohteiden-kohdeosat
  "Palauttaa kohteet tieosoitteen mukaisessa järjestyksessä"
  [kohteet]
  (when kohteet
    (mapv
      (fn [kohde]
        (assoc kohde :kohdeosat (jarjesta-tiet (:kohdeosat kohde))))
      kohteet)))

(defn tie-rampilla?
  "Tarkistaa onko annettu tienumero ramppi. Rampit tunnistetaan tienumeron
  perusteella ja ne ovat välillä 20001-29999."
  [tie]
  (if (and tie (number? tie))
    (boolean (and (> tie 20000)
                  (< tie 30000)))
    false))

(defn tr-osoite-kasvusuuntaan [{:keys [tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys] :as tr-osoite}]
  (let [kasvava-osoite {:tr-alkuosa (if (> tr-alkuosa tr-loppuosa) tr-loppuosa tr-alkuosa)
                        :tr-alkuetaisyys (if (> tr-alkuosa tr-loppuosa) tr-loppuetaisyys tr-alkuetaisyys)
                        :tr-loppuosa (if (> tr-alkuosa tr-loppuosa) tr-alkuosa tr-loppuosa)
                        :tr-loppuetaisyys (if (> tr-alkuosa tr-loppuosa) tr-alkuetaisyys tr-loppuetaisyys)}]
    ;; Palautetaan korjattu tr-osoite. Jos mapissa oli muita avaimia, ne saa jäädä
    (merge tr-osoite kasvava-osoite)))

(defn tr-vali-paakohteen-sisalla?
  "Tarkistaa, että alikohde on kokonaisuudessaan pääkohteen sisällä.
   Olettaa, että molemmat osoitteet ovat samalla tiellä."
  [paakohde alikohde]
  (let [{paa-alkuosa :tr-alkuosa
         paa-alkuetaisyys :tr-alkuetaisyys
         paa-loppuosa :tr-loppuosa
         paa-loppuetaisyys :tr-loppuetaisyys} (tr-osoite-kasvusuuntaan paakohde)
        {ali-alkuosa :tr-alkuosa
         ali-alkuetaisyys :tr-alkuetaisyys
         ali-loppuosa :tr-loppuosa
         ali-loppuetaisyys :tr-loppuetaisyys} (tr-osoite-kasvusuuntaan alikohde)]

    (boolean (and
               ;; Alku- ja loppuosa sisällä
               (>= ali-alkuosa paa-alkuosa)
               (<= ali-loppuosa paa-loppuosa)

               ;; Etäisyydet sisällä jos samalla osalla
               (or
                 (not= ali-alkuosa paa-alkuosa)
                 (>= ali-alkuetaisyys paa-alkuetaisyys))
               (or
                 (not= ali-loppuosa paa-loppuosa)
                 (<= ali-loppuetaisyys paa-loppuetaisyys))))))

(defn tr-vali-paakohteen-sisalla-validaattori [paakohde _ alikohde]
  (when-not (tr-vali-paakohteen-sisalla? paakohde alikohde)
    "Ei pääkohteen sisällä"))

(defn tr-vali-leikkaa-tr-valin?
  "Palauttaa true, mikäli jälkimmäinen tr-väli leikkaa ensimmäisen.
   Leikkaukseksi katsotaan tilanne, jossa osoitteen 2 tie kulkee ainakin hieman osoitteen 1 tien sisällä,
   ei siis riitä, että pelkästään alku/loppu on samassa kohtaa.

   Olettaa, että tr-välit ovat samalla tiellä (ja kaistalla ja ajoradalla)."
  [tr-vali1 tr-vali2]
  (let [tr-vali1 (tr-osoite-kasvusuuntaan tr-vali1)
        tr-vali2 (tr-osoite-kasvusuuntaan tr-vali2)
        ;; Väli 2 ei varmasti leikkaa väliä 1, jos se päättyy ennen välin 1 alkua tai alkaa välin 2 jälkeen
        ;; Tutkitaan siis se, ja annetaan vastaus käänteisenä.
        ei-leikkaa? (or
                      (or (< (:tr-loppuosa tr-vali2) (:tr-alkuosa tr-vali1))
                          (and (= (:tr-loppuosa tr-vali2) (:tr-alkuosa tr-vali1))
                               (<= (:tr-loppuetaisyys tr-vali2) (:tr-alkuetaisyys tr-vali1))))
                      (or (> (:tr-alkuosa tr-vali2) (:tr-loppuosa tr-vali1))
                          (and (= (:tr-alkuosa tr-vali2) (:tr-loppuosa tr-vali1))
                               (>= (:tr-alkuetaisyys tr-vali2) (:tr-loppuetaisyys tr-vali1)))))]
    (boolean (not ei-leikkaa?))))

(defn pistemainen? [{:keys [tr-alkuosa tr-alkuetaisyys tr-loppuosa tr-loppuetaisyys]}]
  (or (and (number? tr-alkuosa)
           (number? tr-alkuetaisyys)
           (nil? tr-loppuosa)
           (nil? tr-loppuetaisyys))
      (and (number? tr-alkuosa)
           (number? tr-alkuetaisyys)
           (number? tr-loppuosa)
           (number? tr-loppuetaisyys)
           (= tr-loppuosa tr-alkuosa)
           (= tr-loppuetaisyys tr-alkuetaisyys))))

(defn kohdeosat-paalekkain? [osa-yksi osa-kaksi]
  (if (and (= (:tr-numero osa-yksi) (:tr-numero osa-kaksi))
           (= (:tr-ajorata osa-yksi) (:tr-ajorata osa-kaksi))
           (= (:tr-kaista osa-yksi) (:tr-kaista osa-kaksi)))
    (cond
      (pistemainen? osa-yksi) (tr-vali-paakohteen-sisalla? osa-kaksi osa-yksi)
      (pistemainen? osa-kaksi) (tr-vali-paakohteen-sisalla? osa-yksi osa-kaksi)
      :else (tr-vali-leikkaa-tr-valin? osa-yksi osa-kaksi))
    false))

(defn alikohteet-tayttamaan-kohde
  "Ottaa pääkohteen ja sen alikohteet. Muokkaa alikohteita niin, että alikohteet täyttävät koko pääkohteen.
   Palauttaa korjatut kohteet. Olettaa, että pääkohde alikohteineen on samalla tiellä."
  ([paakohde alikohteet]
   (cond
     (empty? alikohteet)
     []

     (pistemainen? paakohde)
     ;; Tunkataan ensimmäinen alikohde pistemäiseksi.
     ;; Mikäli alikohteita on ollut useita, niin muiden tiedot häviää.
     ;; Tälle ei oikein voi mitään, mikäli pätkäkohde muokataan pistemäiseksi
     [(assoc (first alikohteet)
        :tr-alkuosa (:tr-alkuosa paakohde)
        :tr-alkuetaisyys (:tr-alkuetaisyys paakohde)
        :tr-loppuosa (:tr-loppuosa paakohde)
        :tr-loppuetaisyys (:tr-loppuetaisyys paakohde))]

     ;;  Oletuskeissi, jossa pääkohde on reitillinen. Muokkaus tehdään seuraavasti:
     ;; - Alikohteet, jotka ovat täysin pääkohteen ulkopuolella, poistetaan
     ;; - Tämän jälkeen ensimmäinen alikohde asetetaan alkamaan pääkohteen alusta ja viimeinen alikohde päättymään
     ;; pääkohteen loppuun.
     :default
     (let [paakohde (tr-osoite-kasvusuuntaan paakohde)
           alikohteet (map tr-osoite-kasvusuuntaan alikohteet)
           alikohteet-jarjestyksessa (sort-by (juxt :tr-alkuosa :tr-alkuetaisyys) alikohteet)
           leikkaavat-alikohteet (filter #(tr-vali-leikkaa-tr-valin? paakohde %) alikohteet-jarjestyksessa)
           ensimmainen-alikohde (first leikkaavat-alikohteet)
           viimeinen-alikohde (last leikkaavat-alikohteet)
           ensimmainen-alikohde-venytettyna (assoc ensimmainen-alikohde
                                              :tr-alkuosa (:tr-alkuosa paakohde)
                                              :tr-alkuetaisyys (:tr-alkuetaisyys paakohde))
           viimeinen-alikohde-venytettyna (assoc viimeinen-alikohde
                                            :tr-loppuosa (:tr-loppuosa paakohde)
                                            :tr-loppuetaisyys (:tr-loppuetaisyys paakohde))
           valiin-jaavat-aikohteet (or (butlast (rest leikkaavat-alikohteet)) [])]

       (if (= ensimmainen-alikohde viimeinen-alikohde)
         ;; Jos leikkaavia alikoihteita on vain yksi, palautetaan se venytettynä kattamaan koko pääkohde
         [(assoc ensimmainen-alikohde-venytettyna
            :tr-loppuosa (:tr-loppuosa viimeinen-alikohde-venytettyna)
            :tr-loppuetaisyys (:tr-loppuetaisyys viimeinen-alikohde-venytettyna))]
         ;; Muutoin venytetään ensimmäinen kohteen alkuun ja viimeinen kohteen loppuun
         (concat [ensimmainen-alikohde-venytettyna] valiin-jaavat-aikohteet [viimeinen-alikohde-venytettyna]))))))

(defn alikohteet-tayttamaan-kutistunut-paakohde
  "Ottaa pääkohteen ja sen SAMAN TIEN alikohteet. Muokkaa alikohteita seuraavasti:
   mikäli jokin alikohde on pidempi kuin pääkohde (alusta tai lopusta), kutistaa sen pääkohteen sisään.

   Palauttaa korjatut kohteet."
  ([paakohde alikohteet]
   (assert (every? #(or (nil? %)
                        (= % (:tr-numero paakohde)))
                   (keep :tr-numero alikohteet))
           (str "Kaikki alikohteet tulee olla samalla tiellä! Kohteen tie: " (:tr-numero paakohde) " ja alikohteiden tiet: " (vec (keep :tr-numero alikohteet))))
   (cond
     (empty? alikohteet)
     []

     (pistemainen? paakohde)
     ;; Tunkataan ensimmäinen alikohde pistemäiseksi.
     ;; Mikäli alikohteita on ollut useita, niin muiden tiedot häviää.
     ;; Tälle ei oikein voi mitään, mikäli pätkäkohde muokataan pistemäiseksi
     [(assoc (first alikohteet)
        :tr-alkuosa (:tr-alkuosa paakohde)
        :tr-alkuetaisyys (:tr-alkuetaisyys paakohde)
        :tr-loppuosa (:tr-loppuosa paakohde)
        :tr-loppuetaisyys (:tr-loppuetaisyys paakohde))]

     ;;  Oletuskeissi, jossa pääkohde on reitillinen. Muokkaus tehdään seuraavasti:
     ;; - Alikohteet, jotka ovat täysin pääkohteen ulkopuolella, poistetaan
     ;; - Tämän jälkeen varmistetaan, että jokainen alikohde on pääkohteen sisällä. Mikäli menee jommasta
     ;; kummasta päästä yli, kutistetaan kohteen sisälle.
     :default
     (let [paakohde (tr-osoite-kasvusuuntaan paakohde)
           alikohteet (map tr-osoite-kasvusuuntaan alikohteet)
           alikohteet-jarjestyksessa (sort-by (juxt :tr-alkuosa :tr-alkuetaisyys) alikohteet)
           leikkaavat-alikohteet (filter #(tr-vali-leikkaa-tr-valin? paakohde %) alikohteet-jarjestyksessa)]

       (map (fn [alikohde]
              (cond-> alikohde
                      (< (:tr-alkuosa alikohde) (:tr-alkuosa paakohde))
                      (assoc :tr-alkuosa (:tr-alkuosa paakohde)
                             :tr-alkuetaisyys (:tr-alkuetaisyys paakohde))

                      (and (= (:tr-alkuosa alikohde) (:tr-alkuosa paakohde))
                           (< (:tr-alkuetaisyys alikohde) (:tr-alkuetaisyys paakohde)))
                      (assoc :tr-alkuetaisyys (:tr-alkuetaisyys paakohde))

                      (> (:tr-loppuosa alikohde) (:tr-loppuosa paakohde))
                      (assoc :tr-loppuosa (:tr-loppuosa paakohde)
                             :tr-loppuetaisyys (:tr-loppuetaisyys paakohde))

                      (and (= (:tr-loppuosa alikohde) (:tr-loppuosa paakohde))
                           (> (:tr-loppuetaisyys alikohde) (:tr-loppuetaisyys paakohde)))
                      (assoc :tr-loppuetaisyys (:tr-loppuetaisyys paakohde))))
            leikkaavat-alikohteet)))))

(defn tieosilla-maantieteellinen-jatkumo?
  "Palauttaa true, mikäli kahdella tieosalla on maantieteellinen jatkumo.
   Käytännössä tämä tarkoittaa sitä, että tieosien päätepisteet ovat riittävän lähellä toisiaan.

   Annetun geometrian tulee olla linestring tai multiline-string (harja.geo-muodossa)

   Vaatii, että molemmat geometriat ovat olemassa."
  [tiegeometria1 tiegeometria2]
  (assert (and tiegeometria1 tiegeometria2) (str "Tiegeometria tyhjä, sain: " tiegeometria1 tiegeometria2))
  (let [threshold 10
        viivat (fn [geo]
                 (case (:type geo)
                   :line [geo]
                   :multiline (:lines geo)))
        geometria1-viivat (viivat tiegeometria1)
        geometria2-viivat (viivat tiegeometria2)]
    (boolean
      (some (fn [geo1-viiva]
              (some (fn [geo2-viiva]
                      (geo/viivojen-paatepisteet-koskettavat-toisiaan? geo1-viiva geo2-viiva threshold))
                    geometria2-viivat))
            geometria1-viivat))))

(defn kohdeosa-paalekkain-muiden-kohdeosien-kanssa
  ([kohdeosa muut-kohdeosat] (kohdeosa-paalekkain-muiden-kohdeosien-kanssa kohdeosa muut-kohdeosat :id))
  ([kohdeosa muut-kohdeosat id-avain]
   (keep #(when (kohdeosat-paalekkain? kohdeosa %)
            {:viesti (str "Kohteenosa on päällekkäin "
                          (if (empty? (:nimi %)) "toisen osan" (str "osan " (:nimi %)))
                          " kanssa")
             :validointivirhe :kohteet-paallekain
             :kohteet (sort-by (juxt :tr-alkuosa :tr-alkuetaisyys id-avain) [% kohdeosa])})
         muut-kohdeosat)))

(defn kohdeosat-keskenaan-paallekkain
  ([kohdeosat] (kohdeosat-keskenaan-paallekkain kohdeosat :id nil))
  ([kohdeosat id-avain] (kohdeosat-keskenaan-paallekkain kohdeosat id-avain nil))
  ([kohdeosat id-avain rivi-indeksi]
   (let [validoitavat-kohdeosat (if rivi-indeksi
                                  (some #(when (= (id-avain %) rivi-indeksi)
                                           [%])
                                        kohdeosat)
                                  kohdeosat)
         paallekkaiset (flatten (for [kohdeosa validoitavat-kohdeosat
                                      :let [muut-kohdeosat (keep (fn [muu-kohdeosa]
                                                                   (when-not (= (id-avain kohdeosa) (id-avain muu-kohdeosa))
                                                                     muu-kohdeosa))
                                                                 kohdeosat)]]
                                  (kohdeosa-paalekkain-muiden-kohdeosien-kanssa kohdeosa muut-kohdeosat id-avain)))]
     (distinct paallekkaiset))))

(def paaluvali-avaimet #{:tr-numero :tr-alkuosa :tr-alkuetaisyys :tr-loppuosa :tr-loppuetaisyys})
(def vali-avaimet (clj-set/union paaluvali-avaimet #{:tr-ajorata :tr-kaista}))
(def alikohteen-tr-sarakkeet-map
  {:tr-numero :tr-numero
   :tr-ajorata :tr-ajorata
   :tr-kaista :tr-kaista
   :tr-alkuosa :tr-alkuosa
   :tr-alkuetaisyys :tr-alkuetaisyys
   :tr-loppuosa :tr-loppuosa
   :tr-loppuetaisyys :tr-loppuetaisyys})