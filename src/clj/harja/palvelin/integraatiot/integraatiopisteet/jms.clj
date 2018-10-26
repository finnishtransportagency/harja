(ns harja.palvelin.integraatiot.integraatiopisteet.jms
  (:require [taoensso.timbre :as log]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.sonja :as sonja]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn kasittele-epaonnistunut-lahetys
  ([lokittaja tapahtuma-id virheviesti] (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id nil virheviesti nil))
  ([lokittaja tapahtuma-id poikkeus virheviesti] (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id poikkeus virheviesti nil))
  ([lokittaja tapahtuma-id poikkeus virheviesti parametrit]
   (log/error poikkeus virheviesti)
   (lokittaja :epaonnistunut nil virheviesti tapahtuma-id nil)
   (virheet/heita-sisainen-kasittelyvirhe-poikkeus
     {:koodi :sonja-lahetys-epaonnistui :viesti virheviesti} parametrit)))

(defn kasittele-poikkeus-lahetyksessa
  ([lokittaja tapahtuma-id poikkeus virheviesti] (kasittele-poikkeus-lahetyksessa lokittaja tapahtuma-id poikkeus virheviesti nil))
  ([lokittaja tapahtuma-id poikkeus virheviesti parametrit]
   (log/error poikkeus virheviesti)
   (lokittaja :epaonnistunut virheviesti (format "Poikkeus: %s" (.getMessage poikkeus)) tapahtuma-id nil)
   (virheet/heita-sisainen-kasittelyvirhe-poikkeus
     {:koodi :sonja-lahetys-epaonnistui :viesti (format "Poikkeus: %s" poikkeus)} parametrit)))

(defn muodosta-viesti [lokittaja tapahtuma-id viesti]
  (if (fn? viesti)
    (try
      (viesti)
      (catch Throwable e
        (let [virheviesti (format "Virhe muodostaessa JMS viestin sisältöä: %s" e)]
          (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id e virheviesti)
          (virheet/heita-sisainen-kasittelyvirhe-poikkeus
            {:koodi :sonja-lahetys-epaonnistui :viesti virheviesti}))))
    viesti))

(defn laheta-jonoon
  ([lokittaja sonja jono viesti] (laheta-jonoon lokittaja sonja jono viesti nil))
  ([lokittaja sonja jono viesti viesti-id]
   (log/debug (format "Lähetetään JMS jonoon: %s viesti: %s." jono viesti))
   (let [tapahtuma-id (lokittaja :alkanut nil nil)
         viesti (muodosta-viesti lokittaja tapahtuma-id viesti)]
     (try+
       (if-let [jms-viesti-id (sonja/laheta sonja jono viesti nil)]
         (do
           ;; Käytetään joko ulkopuolelta annettua ulkoista id:tä tai JMS-yhteyden antamaa id:täs
           (lokittaja :jms-viesti tapahtuma-id (or viesti-id jms-viesti-id) "ulos" viesti jono)
           jms-viesti-id)
         (let [virheviesti (format "Lähetys JMS jonoon: %s epäonnistui. Viesti id:tä ei palautunut" jono)
               parametrit {:viesti-id viesti-id}]
           (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id virheviesti parametrit)))
       (catch [:type :jms-yhteysvirhe] {:keys [virheet]}
         (let [poikkeus (:throwable &throw-context)
               virheviesti (str (format "Lähetys JMS jonoon: %s epäonnistui." jono)
                                (-> virheet first :viesti))
               parametrit {:viesti-id viesti-id}]
           (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id poikkeus virheviesti parametrit)))
       (catch [:type :puutteelliset-multipart-parametrit] {:keys [virheet]}
         (let [poikkeus (:throwable &throw-context)
               virheviesti (str (format "Lähetys JMS jonoon: %s epäonnistui." jono)
                                (-> virheet first :viesti))
               parametrit {:viesti-id viesti-id}]
           (kasittele-epaonnistunut-lahetys lokittaja tapahtuma-id poikkeus virheviesti parametrit)))
       (catch Object _
         (let [poikkeus (:throwable &throw-context)
               virheviesti (format "Tapahtui poikkeus lähettäessä JMS jonoon: %s epäonnistui." jono)
               parametrit {:viesti-id viesti-id}]
           (kasittele-poikkeus-lahetyksessa lokittaja tapahtuma-id poikkeus virheviesti parametrit)))))))

(defn jonolahettaja
  [lokittaja sonja jono]
  (fn [viesti viesti-id]
    (laheta-jonoon lokittaja sonja jono viesti viesti-id)))

(defn kuittausjonokuuntelija [lokittaja sonja jono viestiparseri viesti->id onnistunut? kasittelija]
  (log/debug "Käynnistetään JMS viestikuuntelija kuuntelemaan jonoa: " jono)
  (try
    (sonja/kuuntele! sonja jono
                    (fn [viesti]
                      (log/debug (format "Vastaanotettiin jonosta: %s viesti: %s" jono viesti))
                      (let [multipart-viesti? (try (instance? (Class/forName "progress.message.jimpl.xmessage.MultipartMessage") viesti)
                                                   (catch java.lang.ClassNotFoundException e
                                                     (log/error "Ei löytynyt MultipartMessage luokkaa: " e)
                                                     nil))
                            viestin-sisalto (if multipart-viesti?
                                              ;; Oletuksena on, että multipartvastaus sisältää vain yhden osan
                                              (-> viesti
                                                (.getPart 0)
                                                (.getContentBytes)
                                                (String.))
                                              (.getText viesti))
                            data (viestiparseri viestin-sisalto)
                            viesti-id (viesti->id data)
                            onnistunut (onnistunut? data)]
                        (if viesti-id
                          (lokittaja :saapunut-jms-kuittaus viesti-id viestin-sisalto onnistunut jono)
                          (log/error "Kuittauksesta ei voitu hakea viesti-id:tä."))
                        (kasittelija data viesti-id onnistunut))))
    (catch Exception e
      (log/error e "Jono: %s kuittauskuuntelijassa tapahtui poikkeus."))))

(defn jms-kuuntelu [lokittaja sonja jono-sisaan jono-ulos viestiparseri kuittausmuodostaja kasittelija]
  (log/debug "Käynnistetään JMS kuuntelija jonolle: " jono-sisaan ", kuittaukset lähetetään jonoon: " jono-ulos)
  (try
    (sonja/kuuntele!
      sonja jono-sisaan
      (fn [viesti]
        (log/debug "Vastaanotettiin viesti jonosta " jono-sisaan ": " viesti)
        (let [viestin-sisalto (.getText viesti)
              ulkoinen-id (.getJMSCorrelationID viesti)
              tapahtuma-id (lokittaja :saapunut-jms-viesti ulkoinen-id viestin-sisalto jono-sisaan)
              [onnistui? data] (try
                                 [true (viestiparseri viestin-sisalto)]
                                 (catch Exception e
                                   [false e]))]
          (log/debug "Viesti parsinta onnistui? " onnistui?)
          (if onnistui?
            ;; Viestin parsinta onnistui, yritetään käsitellä se
            (try
              (let [vastaus (kasittelija data)]
                (if (and jono-ulos kuittausmuodostaja)
                  (let [vastauksen-sisalto (kuittausmuodostaja vastaus)]
                    (lokittaja :lahteva-jms-kuittaus vastauksen-sisalto tapahtuma-id true "" jono-ulos)
                    (sonja/laheta sonja jono-ulos vastauksen-sisalto {:correlation-id ulkoinen-id}))
                  (lokittaja :onnistunut nil "" tapahtuma-id ulkoinen-id)))
              (catch Exception e
                ;; Hallitsematon virhe viestin käsittelyssä, kirjataan epäonnistunut integraatio
                (lokittaja :epaonnistunut viestin-sisalto "" tapahtuma-id ulkoinen-id)))

            ;; Viestin parsinta epäonnistui, kirjataan suoraan epäonnistunut integraatio
            (lokittaja :epaonnistunut viestin-sisalto (str "Viestin lukeminen epäonnistui" (.getMessage data))
                       tapahtuma-id ulkoinen-id)))))))

(defn kuuntele [lokittaja sonja jono-sisaan viestiparseri kasittelija]
  (jms-kuuntelu lokittaja sonja jono-sisaan nil viestiparseri nil kasittelija))

(defn kuuntele-ja-kuittaa [lokittaja sonja jono-sisaan jono-ulos viestiparseri kuittausmuodostaja kasittelija]
  (jms-kuuntelu lokittaja sonja jono-sisaan jono-ulos viestiparseri kuittausmuodostaja kasittelija))
