/*
 * The MIT License
 * 
 * Copyright (c) 2004-2009, Sun Microsystems, Inc., Kohsuke Kawaguchi, Luca Domenico Milanesio, Tom Huybrechts
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package hudson.plugins.serverselection;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.ParameterValue;
import hudson.model.Run;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.export.Exported;

import java.util.Locale;

import hudson.util.VariableResolver;
import java.util.Map;

/**
 * {@link ParameterValue} created from {@link StringParameterDefinition}.
 */
public class ServSelParameterValue extends ParameterValue {

    @Exported(visibility = 4)
    public final String server, environ, version;

    @DataBoundConstructor
    public ServSelParameterValue(String name, String server, String environ, String version) {
        this(name, server, environ, version, null);
    }

    public ServSelParameterValue(String name, String server, String environ, String version, String description) {
        super(name, description);
        this.server = server;
        this.environ = environ;
        this.version = version;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((server == null) ? 0 : server.hashCode());
        return result;
    }

    @Override
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        env.put("ENVIRONMENT", environ);
        env.put("VERSION", version);
        buildEnvVars(build, (Map<String, String>) env);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ServSelParameterValue other = (ServSelParameterValue) obj;
        if (server == null) {
            if (other.server != null) {
                return false;
            }
        } else if (!server.equals(other.server) || !environ.equals(other.environ) || !version.equals(other.version)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "(ServSelParameterValue) Server='" + server + "'\nVersion='" + version + "'\nEnvironment='" + environ + "'";
    }

    @Override
    public String getShortDescription() {
        return name + '=' + server;
    }

}
