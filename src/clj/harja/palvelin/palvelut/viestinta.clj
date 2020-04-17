(ns harja.palvelin.palvelut.viestinta
  "Yleisiä apufunktioita domain-asioihin liittyvään sähköposti- ja tekstiviestiviestintään.

   Domain-asioiden tarkemmat viestinvälitysfunktiot löytyvät asioiden omista namespaceista, ks. esim:
   - harja.palvelin.palvelut.laadunseuranta.viestinta
   - harja.palvelin.palvelut.yllapitokohteet.viestinta"
  (:require [taoensso.timbre :as log]
            [clojure.core.match :refer [match]]
            [harja.palvelin.integraatiot.sahkoposti :as sahkoposti]
            [hiccup.core :refer [html]]
            [harja.palvelin.komponentit.fim :as fim]
            [harja.palvelin.integraatiot.labyrintti.sms :as sms]
            [harja.palvelin.integraatiot.api.tyokalut.virheet :as virheet])
  (:use [slingshot.slingshot :only [try+ throw+]]))

(defn laheta-sahkoposti-itselle
  "Lähettää sähköpostivahvistuksen itse käyttäjälle joka sai aikaan mailin lähetyksen Harjasta."
  [{:keys [kopio-viesti email sahkoposti viesti-otsikko viesti-body liite tiedostonimi]}]
  (let [lahetys-fn (if liite
                     sahkoposti/laheta-viesti-ja-liite!
                     sahkoposti/laheta-viesti!)
        viestin-vartalo (str kopio-viesti "\n"
                             viesti-body)
        viesti (if liite
                 {:viesti viestin-vartalo
                  :pdf-liite liite}
                 viestin-vartalo)
        argumentit [email (sahkoposti/vastausosoite email) sahkoposti (str "Harja-viesti lähetetty: " viesti-otsikko) viesti]
        argumentit (if liite (conj argumentit tiedostonimi) argumentit)]
    (try
      (apply lahetys-fn argumentit)
      (catch Exception e
        (log/error (format "Sähköpostin lähetys osoitteeseen %s epäonnistui. Virhe: %s"
                           (pr-str sahkoposti) (pr-str e)))))))

(defn laheta-sposti-fim-kayttajarooleille
  "Yrittää lähettää sähköpostin annetun urakan FIM-käyttäjille, jotka ovat
   annetussa roolissa. Jos viestin lähetys epäonnistuu, logittaa virheen ja heittää Exceptionin.

   Parametrit:
   fim                    FIM-komponentti
   email                  Sähköpostikomponentti (sonja-sahkoposti)
   urakka-sampoid         Sen urakan sampo-id, jonka käyttäjiä etsitään FIMistä
   fim-kayttajaroolit     Setti rooleja, joissa oleville henkilöille viesti lähetetään. Huomioi kirjoitusasu!
                          Esim. #{\"urakan vastuuhenkilö\"}
   viesti-otsikko         Sähköpostiviestin otsikko, johon lisätään prefix 'Harja: '
   viesti-body            Sähköpostiviestin body"
  [{:keys [fim email urakka-sampoid fim-kayttajaroolit viesti-otsikko viesti-body]}]
  (log/debug (format "Lähetetään sähköposti FIM-käyttäjille %s. Aihe: %s" fim-kayttajaroolit viesti-otsikko))
  (try
    (let [viestin-saajat (fim/hae-urakan-kayttajat-jotka-roolissa fim urakka-sampoid fim-kayttajaroolit)]
      (if (empty? viestin-saajat)
        (log/warn (format "Urakalle %s ei löydy FIM:stä yhtään henkiöä, jolle lähettää sähköposti." urakka-sampoid))
        (doseq [henkilo viestin-saajat]
          (sahkoposti/laheta-viesti!
            email
            (sahkoposti/vastausosoite email)
            (:sahkoposti henkilo)
            (str "Harja: " viesti-otsikko)
            viesti-body)
          )))
    (catch Exception e
      (log/error (format "Sähköpostia ei voitu lähettää urakan %s FIM-käyttäjille %s. Virhe: %s"
                         urakka-sampoid fim-kayttajaroolit (pr-str e)))
      (throw e)
      )))

(defn laheta-tekstiviesti-fim-kayttajarooleille
  "Yrittää lähettää tekstiviestin annetun urakan FIM-käyttäjille, jotka ovat
   annetussa roolissa. Jos viestin lähetys epäonnistuu, logittaa virheen.

   Parametrit:
   fim                    FIM-komponentti
   sms                    SMS-komponentti
   urakka-sampoid         Sen urakan sampo-id, jonka käyttäjiä etsitään FIMistä
   fim-kayttajaroolit     Setti rooleja, joissa oleville henkilöille viesti lähetetään. Huomioi kirjoitusasu!
                          Esim. #{\"urakan vastuuhenkilö\"}
   viesti                 Tekstiviestin sisältö"
  [{:keys [fim sms urakka-sampoid fim-kayttajaroolit viesti-otsikko viesti]}]
  (log/debug (format "Lähetetään tekstiviesti FIM-käyttäjille %s. Aihe: %s" fim-kayttajaroolit viesti-otsikko))
  (try
    (let [viestin-saajat (fim/hae-urakan-kayttajat-jotka-roolissa fim urakka-sampoid fim-kayttajaroolit)]
      (if (empty? viestin-saajat)
        (log/warn (format "Urakalle %s ei löydy FIM:stä yhtään henkiöä, jolle lähettää tekstiviesti." urakka-sampoid))
        (doseq [henkilo viestin-saajat]
          (try
            (sms/laheta sms (:puhelin henkilo) viesti)
            (catch Exception e
              (log/error (format "Tekstiviestin lähetys FIM-käyttäjälle %s epäonnistui. Virhe: %s"
                                 (pr-str henkilo) (pr-str e))))))))
    (catch Exception e
      (log/error (format "Tekstiviestiä ei voitu lähettää urakan %s FIM-käyttäjille %s. Virhe: %s"
                         urakka-sampoid fim-kayttajaroolit (pr-str e))))))

