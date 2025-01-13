package practicaexist;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XPathQueryService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        try {
            String driver = "org.exist.xmldb.DatabaseImpl";
            Collection col = null;
            String URI = "xmldb:exist://localhost:8080/exist/xmlrpc/db/gimnasio";
            String user = "admin";
            String pass = "";
            try {
                Class cl = Class.forName(driver);
                Database database = (Database) cl.newInstance();
                DatabaseManager.registerDatabase(database);
            } catch (Exception e) {
                System.out.println("Error al inicializar la BD eXist");
                e.printStackTrace();
            }
            col = DatabaseManager.getCollection(URI, user, pass);
            if(col == null)
                System.out.println("No existe la colección.");
            generarXMLIntermedio(col);
            subirXML(col,"src/main/resources/xml/fichero.xml");
            generarXMLFinal(col);
            subirXML(col, "src/main/resources/xml/ficheroModificado.xml");
            col.close();
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void generarXMLIntermedio(Collection col) throws XMLDBException {
        XPathQueryService servicio = (XPathQueryService) col.getService("XPathQueryService", "1.0");
        try {
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;
            docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element datosPrincipal = doc.createElement("datosPrincipal");
            doc.appendChild(datosPrincipal);

            String query = "for $uso in /USO_GIMNASIO/fila_uso return $uso";
            ResourceSet result = servicio.query(query);
            ResourceIterator i;
            i = result.getIterator();

            if (!i.hasMoreResources()) {
                System.out.println("La consulta está vacía");
                return;
            }

            while (i.hasMoreResources()) {
                String codigoSocio = "";
                String nombreSocio = "";
                String codigoActividad = "";
                String nombreActividad = "";
                String tipoActividad = "";
                int cantidad;
                Element datos = doc.createElement("datos");
                datosPrincipal.appendChild(datos);
                Resource r = i.nextResource();
                String contenido = (String) r.getContent();

                codigoSocio = contenido.split("<CODSOCIO>")[1].split("</CODSOCIO>")[0];

                String querySocio = "/SOCIOS_GIM/fila_socios[COD='"+codigoSocio+"']/NOMBRE/text()";
                ResourceSet resultSocio = servicio.query(querySocio);
                ResourceIterator iSocio = resultSocio.getIterator();

                if (iSocio.hasMoreResources()) {
                    Resource rSocio = iSocio.nextResource();
                    String contenidoSocio = (String) rSocio.getContent();
                    nombreSocio = contenidoSocio.trim();
                }

                codigoActividad = contenido.split("<CODACTIV>")[1].split("</CODACTIV>")[0];

                String queryActividad = "/ACTIVIDADES_GIM/fila_actividades[@cod="+codigoActividad+"]/NOMBRE/text()";
                ResourceSet resultActividad = servicio.query(queryActividad);
                ResourceIterator iActividad = resultActividad.getIterator();

                if (iActividad.hasMoreResources()) {
                    Resource rActividad = iActividad.nextResource();
                    String contenidoActividad = (String) rActividad.getContent();
                    nombreActividad = contenidoActividad.trim();
                }

                String horaInicio = contenido.split("<HORAINICIO>")[1].split("</HORAINICIO>")[0];
                String horaFin = contenido.split("<HORAFINAL>")[1].split("</HORAFINAL>")[0];
                int horas = Integer.parseInt(horaFin)-Integer.parseInt(horaInicio);

                String queryActividadTipo = "/ACTIVIDADES_GIM/fila_actividades[@cod='"+codigoActividad+"']/string(@tipo)";
                ResourceSet resultActividadTipo = servicio.query(queryActividadTipo);
                ResourceIterator iActividadTipo = resultActividadTipo.getIterator();

                if (iActividadTipo.hasMoreResources()) {
                    Resource rActividad = iActividadTipo.nextResource();
                    tipoActividad = rActividad.getContent().toString().trim();
                }

                aniadirElemento(doc, datos, "COD", codigoSocio);
                aniadirElemento(doc, datos, "NOMBRESOCIO", nombreSocio);
                aniadirElemento(doc, datos, "CODACTIV", codigoActividad);
                aniadirElemento(doc, datos, "NOMBREACTIVIDAD", nombreActividad);
                aniadirElemento(doc, datos, "horas", horas+"");

                switch (Integer.parseInt(tipoActividad)) {
                    case 1:
                        cantidad = 0;
                        aniadirElemento(doc, datos, "tipoact", "libre horario");
                        break;
                    case 2:
                        cantidad = 2;
                        aniadirElemento(doc, datos, "tipoact", "grupo");
                        break;
                    default:
                        cantidad = 4;
                        aniadirElemento(doc, datos, "tipoact", "alquila un espacio");
                        break;
                }

                aniadirElemento(doc, datos, "cuota_adicional", (cantidad*horas)+"€");
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);

            StreamResult streamResult = new StreamResult(new File("src/main/resources/xml/fichero.xml"));
            transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
            transformer.transform(source, streamResult);
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void subirXML(Collection col,String ruta) {
        File f = new File(ruta);
        if(!f.canRead()) {
            System.err.println("Error");
        } else {
            try {
                Resource nuevoRecurso = col.createResource(f.getName(), "XMLResource");
                nuevoRecurso.setContent(f);
                col.storeResource(nuevoRecurso);
            } catch (XMLDBException e) {
                e.printStackTrace();
            }
        }
    }

    private static void generarXMLFinal(Collection col) {
        try {
            XPathQueryService servicio = (XPathQueryService) col.getService("XPathQueryService", "1.0");
            DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder docBuilder;
            docBuilder = docFactory.newDocumentBuilder();
            Document doc = docBuilder.newDocument();
            Element datosPrincipal = doc.createElement("datosPrincipal");
            doc.appendChild(datosPrincipal);
            String query = "for $persona in /SOCIOS_GIM/fila_socios return $persona";
            ResourceSet result = servicio.query(query);
            ResourceIterator i;
            i = result.getIterator();

            if (!i.hasMoreResources()) {
                System.out.println("La consulta está vacía");
                return;
            }

            while (i.hasMoreResources()) {
                String codigoSocio = "";
                String nombreSocio = "";
                String cuotaFija = "";
                String sumaCuotaAdicional = "";
                int cuotaTotal;
                Element datos = doc.createElement("datos");
                datosPrincipal.appendChild(datos);
                Resource r = i.nextResource();
                String contenido = (String) r.getContent();

                codigoSocio = contenido.split("<COD>")[1].split("</COD>")[0];

                nombreSocio = contenido.split("<NOMBRE>")[1].split("</NOMBRE>")[0];

                cuotaFija = contenido.split("<CUOTA_FIJA>")[1].split("</CUOTA_FIJA>")[0];

                String queryCuota = "sum(/datosPrincipal/datos[COD='"+codigoSocio+"']/number(translate(cuota_adicional, '€', '')))";
                ResourceSet resultCuota = servicio.query(queryCuota);
                ResourceIterator iCuota = resultCuota.getIterator();
                if (iCuota.hasMoreResources()) {
                    Resource rCuota = iCuota.nextResource();
                    String contenidoCuota = (String) rCuota.getContent();
                    sumaCuotaAdicional = contenidoCuota.trim();
                }

                cuotaTotal = Integer.parseInt(cuotaFija)+Integer.parseInt(sumaCuotaAdicional);

                aniadirElemento(doc, datos, "COD", codigoSocio);
                aniadirElemento(doc, datos, "NOMBRESOCIO", nombreSocio);
                aniadirElemento(doc, datos, "CUOTA_FIJA", cuotaFija);
                aniadirElemento(doc, datos, "suma_cuota_adic", sumaCuotaAdicional);
                aniadirElemento(doc, datos, "cuota_total", cuotaTotal+"");
            }

            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            DOMSource source = new DOMSource(doc);

            StreamResult streamResult = new StreamResult(new File("src/main/resources/xml/ficheroModificado.xml"));
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(source, streamResult);
        }catch(Exception e) {
            e.printStackTrace();
        }
    }

    private static void aniadirElemento(Document doc, Element rowElement, String header, String texto) {
        Element elemento = doc.createElement(header);
        elemento.appendChild(doc.createTextNode(texto));
        rowElement.appendChild(elemento);
    }
}
