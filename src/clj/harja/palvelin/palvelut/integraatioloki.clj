(ns harja.palvelin.palvelut.integraatioloki
  (:require [com.stuartsierra.component :as component]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clj-time.core :as t]
            [clj-time.coerce :as tc]
            [harja.palvelin.komponentit.http-palvelin :refer [julkaise-palvelu poista-palvelu]]
            [harja.kyselyt.integraatioloki :as q]
            [harja.kyselyt.konversio :as konversio]
            [harja.domain.oikeudet :as oikeudet]
            [harja.palvelin.palvelut.tloik.toimenpiteen-lahetyksen-kuittausanalyysi :as tloik-kuittausanalyysi]
            [harja.pvm :as pvm]))


(defn muunna-merkkijono-kartaksi [merkkijono]
  ;; todo: read-stringing käyttö voi olla turvatonta. kysy tatulta parempi tapa.
  (if (and merkkijono (not (empty? merkkijono)))
    (let [kartta (binding [*read-eval* false] (read-string merkkijono))]
      kartta)
    nil))

(def viesti-xf
  (comp
    (map #(assoc % :parametrit (muunna-merkkijono-kartaksi (:parametrit %))))
    (map #(assoc % :otsikko (muunna-merkkijono-kartaksi (:otsikko %))))
    (map (fn [v]
            (if (or
                  (and (not (nil? (:sisaltotyyppi v))) (= "application/xml" (:sisaltotyyppi v)))
                  (and (not (nil? (:sisalto v))) (str/includes? (:sisalto v) "<?xml")))
              (assoc v :sisalto (konversio/prettyprint-xml (:sisalto v)))
              v)))))

(def tapahtuma-xf
  (comp
    (map konversio/alaviiva->rakenne)
    (map #(assoc % :onnistunut (boolean (Boolean/valueOf (:onnistunut %)))))))

(defn hae-integraatiot [db]
  (let [integraatiot (q/hae-jarjestelmien-integraatiot db)
        uniikit-integraatiot (mapv (fn [kartta]
                                     (assoc kartta :integraatiot
                                                   (mapv #(:integraatio %)
                                                         (into []
                                                               (filter #(= (:jarjestelma %) (:jarjestelma kartta))) integraatiot))))
                                   (set (map #(dissoc % :integraatio) integraatiot)))]
    (log/debug "Integraatiot:" uniikit-integraatiot)
    uniikit-integraatiot))

(defn hae-jarjestelmien-integraatiot
  "Palvelu, joka palauttaa kaikki eri järjestelmien integraatiot."
  [db kayttaja]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (log/debug "Haetaan järjestelmien integraatiot.")
  (hae-integraatiot db))

(defn hae-integraatiotapahtumat
  "Palvelu, joka palauttaa järjestelmän integraation tapahtumat tietyltä aikaväliltä."
  [db kayttaja jarjestelma integraatio alkaen paattyen hakuehdot]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (let [{:keys [otsikot parametrit viestin-sisalto tapahtumien-tila tapahtumien-kesto max-tulokset]} hakuehdot
        otsikot (if (str/blank? otsikot) nil otsikot)
        parametrit (if (str/blank? parametrit) nil parametrit)
        viestin-sisalto (if (str/blank? viestin-sisalto) nil viestin-sisalto)
        onnistuneet (case tapahtumien-tila
                      :onnistuneet true
                      :epaonnistuneet false
                      nil)
        kesken? (= tapahtumien-tila :kesken)
        limit 500
        tapahtumien-kesto (when tapahtumien-kesto
                            (/ (t/in-millis (t/minutes tapahtumien-kesto)) 1000))
        tapahtumat (into []
                     tapahtuma-xf
                     (q/hae-jarjestelman-integraatiotapahtumat-aikavalilla db
                       {:jarjestelma jarjestelma
                        :integraatio integraatio
                        :onnistunut onnistuneet
                        :kesken kesken?
                        :alkaen (konversio/sql-date alkaen)
                        :paattyen (konversio/sql-date paattyen)
                        :otsikot otsikot
                        :parametrit parametrit
                        :sisalto viestin-sisalto
                        :kesto tapahtumien-kesto
                        :limit limit}))]
    tapahtumat))


(def granulariteetti->intervalli
  "Granulariteetti-keyword → PostgreSQL date_bin -yhteensopiva interval-merkkijono."
  {:minute "1 minute"
   :5-min  "5 minutes"
   :15-min "15 minutes"
   :30-min "30 minutes"
   :hour   "1 hour"
   :3-hour "3 hours"
   :day    "1 day"})

(defn- laske-granulariteetti
  "Palauttaa granulariteetti-keywordin aikavälin pituuden perusteella.
  Granulariteetti määrittää, kuinka tapahtumat ryhmitellään aikavälillä: minuutti, 5 minuuttia, tunti, päivä, jne."
  [alkaen paattyen]
  (if (and alkaen paattyen)
    (let [minuutit (pvm/aikaa-valissa (tc/from-date alkaen) (tc/from-date paattyen) t/in-minutes)
          tunnit (/ minuutit 60)]
      (cond
        (<= tunnit 0.5) :minute
        (<= tunnit 1) :5-min
        (<= tunnit 3) :15-min
        (<= tunnit 6) :30-min
        (<= tunnit 24) :hour
        (<= tunnit 48) :3-hour
        :else :day))
    :day))

(defn hae-integraatiotapahtumien-maarat
  [db kayttaja jarjestelma integraatio alkaen paattyen]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (let [jarjestelma (when jarjestelma (:jarjestelma jarjestelma))
        granulariteetti (laske-granulariteetti alkaen paattyen)
        maarat (q/hae-integraatiotapahtumien-maarat
                 db
                 {:bin-intervalli (get granulariteetti->intervalli granulariteetti)
                  :jarjestelma_annettu (boolean jarjestelma)
                  :jarjestelma jarjestelma
                  :integraatio_annettu (boolean integraatio)
                  :integraatio integraatio
                  :alkaen (konversio/sql-date alkaen)
                  :paattyen (konversio/sql-date paattyen)})]
    {:granulariteetti granulariteetti
     :maarat maarat}))

(defn hae-integraatiotapahtuman-viestit [db kayttaja tapahtuma-id]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (into []
        viesti-xf
        (q/hae-integraatiotapahtuman-viestit db tapahtuma-id)))

(def tloik-duplikaattikuittausmaksimi 1000)

(defn- vektoroi-tulos [arvo]
  (or (konversio/pgarray->vector arvo)
      (when (vector? arvo) arvo)
      (when (sequential? arvo) (vec arvo))
      []))

(defn- muodosta-esimerkkitapahtuma [rivi]
  {:tapahtuma-id (:tapahtumaid rivi)
   :alkanut (:alkanut rivi)
   :ulkoinen-id (:ulkoinenid rivi)})

(defn- muodosta-epailty-duplikaattikuittausryhma-yhteenvedosta [yhteenveto esimerkkitapahtumat]
  {:ilmoitusid (:ilmoitusid yhteenveto)
   :kuittaustyyppi (:kuittaustyyppi yhteenveto)
  :kanava (:kanava yhteenveto)
  :duplikaatteja (or (:duplikaatteja yhteenveto) 0)
  :kertyneet-lahetysvirheet (or (:kertyneet_lahetysvirheet yhteenveto) 0)
  :uniikit-kuittaajat (or (:uniikit_kuittaajat yhteenveto) 0)
   :ensimmainen-alkanut (:ensimmainen_alkanut yhteenveto)
   :viimeisin-alkanut (:viimeisin_alkanut yhteenveto)
   :uniikit-ulkoiset-idt (vektoroi-tulos (:uniikit_ulkoiset_idt yhteenveto))
   :esimerkkitapahtumat (mapv muodosta-esimerkkitapahtuma esimerkkitapahtumat)})

(defn- muodosta-epaillyt-duplikaattikuittaukset-yhteenvedoista [yhteenvedot esimerkkitapahtumat tilastot katkaistu]
  (let [esimerkit-ryhmittain (group-by :ryhmaavain esimerkkitapahtumat)]
    {:katkaistu katkaistu
     :kasitellyt-rivit (or (:kasitellyt_rivit tilastot) 0)
     :ohitetut-rivit (or (:ohitetut_rivit tilastot) 0)
     :ryhmat (mapv (fn [yhteenveto]
                     (muodosta-epailty-duplikaattikuittausryhma-yhteenvedosta
                       yhteenveto
                       (get esimerkit-ryhmittain (:ryhmaavain yhteenveto))))
                   yhteenvedot)}))

(defn hae-epaillyt-duplikaattikuittaukset [db kayttaja alkaen paattyen]
  (oikeudet/vaadi-lukuoikeus oikeudet/hallinta-integraatiotilanne-integraatioloki kayttaja)
  (let [parametrit {:alkaen (konversio/sql-date alkaen)
                    :paattyen (konversio/sql-date paattyen)}
        yhteenvedot (vec
                      (q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausyhteenvedot
                        db
                        (assoc parametrit :limit (inc tloik-duplikaattikuittausmaksimi))))
        katkaistu (> (count yhteenvedot) tloik-duplikaattikuittausmaksimi)
        yhteenvedot (if katkaistu
                      (subvec yhteenvedot 0 tloik-duplikaattikuittausmaksimi)
                      yhteenvedot)
        tilastot (q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausten-tilastot db parametrit)
        ryhmaavaimet (mapv :ryhmaavain yhteenvedot)
        esimerkkitapahtumat (if (seq ryhmaavaimet)
                              (vec
                                (q/hae-tloik-toimenpiteen-lahetyksen-duplikaattikuittausten-esimerkkitapahtumat
                                  db
                                  (assoc parametrit :ryhmaavaimet ryhmaavaimet)))
                              [])]
    (muodosta-epaillyt-duplikaattikuittaukset-yhteenvedoista yhteenvedot esimerkkitapahtumat tilastot katkaistu)))



(defrecord Integraatioloki []
  component/Lifecycle
  (start [this]
    (let [db (:db-replica this)
          http-palvelin (:http-palvelin this)
          hae-tloik-kuittausanalyysi (fn [kayttaja {:keys [alkaen paattyen]}]
                                       (tloik-kuittausanalyysi/hae-toimenpiteen-lahetyksen-kuittausanalyysi
                                         db kayttaja alkaen paattyen))]
      (julkaise-palvelu http-palvelin
                        :hae-jarjestelmien-integraatiot
                        (fn [kayttaja _]
                          (hae-jarjestelmien-integraatiot db kayttaja)))
      (julkaise-palvelu http-palvelin
                        :hae-integraatiotapahtumat
                        (fn [kayttaja {:keys [jarjestelma integraatio alkaen paattyen hakuehdot]}]
                          (hae-integraatiotapahtumat db kayttaja jarjestelma integraatio alkaen paattyen hakuehdot)))
      (julkaise-palvelu http-palvelin
                        :hae-integraatiotapahtumien-maarat
                        (fn [kayttaja {:keys [jarjestelma integraatio alkaen paattyen]}]
                          (hae-integraatiotapahtumien-maarat db kayttaja jarjestelma integraatio alkaen paattyen)))
      (julkaise-palvelu http-palvelin
                        :hae-integraatiotapahtuman-viestit
                        (fn [kayttaja tapahtuma-id]
                          (hae-integraatiotapahtuman-viestit db kayttaja tapahtuma-id)))
      (julkaise-palvelu http-palvelin
                        tloik-kuittausanalyysi/kuittausanalyysi-endpoint
                        hae-tloik-kuittausanalyysi)
      (julkaise-palvelu http-palvelin
                        tloik-kuittausanalyysi/legacy-kuittausanalyysi-endpoint
                        hae-tloik-kuittausanalyysi))
    this)

  (stop [this]
    (poista-palvelu (:http-palvelin this) :hae-jarjestelmien-integraatiot)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtumat)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtumien-maarat)
    (poista-palvelu (:http-palvelin this) :hae-integraatiotapahtuman-viestit)
    (poista-palvelu (:http-palvelin this) tloik-kuittausanalyysi/kuittausanalyysi-endpoint)
    (poista-palvelu (:http-palvelin this) tloik-kuittausanalyysi/legacy-kuittausanalyysi-endpoint)
    this))
