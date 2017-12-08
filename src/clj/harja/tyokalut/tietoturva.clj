(ns harja.tyokalut.tietoturva
  (:require [specql.core :refer [fetch]]
            [specql.core :as specql]))

(defn tarkista-linkitys
  "Tarkistaa, että taulussa oleva rivi linkittyy annettuun id-arvoon.
   Jos ei linkity, heittää poikkeuksen.

   Tällä voi tarkistaa esim. sen, että päivitettävä rivi kuuluu siihen urakkaan, johon
   oikeustarkistus on tehty."
  [db taulu id-sarake id-arvo linkitys-sarake vaadittu-linkki-id]
  (assert vaadittu-linkki-id "Linkki-id puuttuu!")

  (when id-arvo ;; Jos id:tä ei ole annettu, linkitystä on turha tarkistaa
    (let [taulu-rivi (first (specql/fetch db taulu
                                          #{linkitys-sarake}
                                          {id-sarake id-arvo}))
          linkki-id (linkitys-sarake taulu-rivi)]

      (when (not= linkki-id vaadittu-linkki-id)
        (throw (SecurityException. (str "Annettussa taulussa " taulu
                                        " oleva rivi id:llä " id-arvo
                                        " ei linkity " linkitys-sarake "=" vaadittu-linkki-id
                                        " vaan " linkitys-sarake "=" linkki-id)))))))